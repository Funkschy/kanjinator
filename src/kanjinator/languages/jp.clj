(ns kanjinator.languages.jp
  (:require
   [clojure.tools.logging :as log]
   [kanjinator.config :refer [config windows?]]
   [kanjinator.dictionaries.jisho :refer [lookup-word-in-dictionary]]
   [kanjinator.language :refer [Language]]
   [kanjinator.preprocess :refer [add-white-margin grayscale invert-if-needed
                                  preprocess-image scale sharpen threshold]])
  (:import
   [com.atilika.kuromoji.ipadic Token Tokenizer]
   [java.awt.image BufferedImage]
   [net.sourceforge.tess4j Tesseract]
   [net.sourceforge.tess4j.util LoadLibs]))

(def ^:private parts-of-speech-1
  {"名詞"   :noun           ;; めいし
   "助詞"   :particle       ;; じょし
   "形容詞" :adjective      ;; けいようし
   "副詞"   :adverb         ;; ふくし
   "助動詞" :auxiliary-verb ;; inflecting dependent word = じょどうし (e.g です)
   "動詞"   :verb})         ;; どうし

(def ^:private parts-of-speech-2
  {"自立"    :independent             ;; じりつ
   "非自立"   :dependent              ;; not independent = ひじりつ
   "接続助詞" :conjugation-particle}) ;;  せつぞくじょし

(defn- part-of-speech-1 [^Token t]
  (parts-of-speech-1 (.getPartOfSpeechLevel1 t)))

(defn- part-of-speech-2 [^Token t]
  (parts-of-speech-2 (.getPartOfSpeechLevel2 t)))

(defn- segment [part-wanted? text]
  (log/info "segmenting:" text)
  (let [tokens (.tokenize (new Tokenizer) text)
        base   (fn [^Token t]
                 (let [b (.getBaseForm t)]
                   (if (= "*" b) ;; this will be returned e.g if the word is 'unknown'
                     (.getSurface t)
                     b)))
        take?  (fn [^Token t]
                 (and (part-wanted? (part-of-speech-1 t))
                      ;; those are just grammatical forms after a verb that might be classified
                      ;; as separate verbs, e.g. なっている is classified as なっ, て, いる, which
                      ;; isn't really correct, because it's just the te-iru form of なる
                      ;; the て is a conjugation-particle and the いる is a dependent verb, so
                      ;; we can just filter those
                      (not (#{:dependent :conjugation-particle} (part-of-speech-2 t)))))
        xf     (comp (filter take?) (map base))]
    ;; I know some people hate commented code, but I actually use this for debugging quite frequently
    ;; (doseq [^Token t tokens]
    ;;   (println (.getPartOfSpeechLevel1 t) (.getSurface t) (.getBaseForm t) (.getPartOfSpeechLevel2 t)))
    (transduce xf conj tokens)))

(def ^:private trash-file
  (if windows?
    "nul"
    "/dev/null"))

(defn- new-tesseract ^Tesseract [page-seg-mode language]
  (doto (new Tesseract)
    (.setVariable "debug_file" trash-file)
    (.setDatapath (.getAbsolutePath (LoadLibs/extractTessResources "tessdata")))
    (.setLanguage language)
    (.setPageSegMode page-seg-mode)
    (.setOcrEngineMode 1)))

(def ^:private jp-regex #"([\p{IsHan}\p{IsBopo}\p{IsHira}\p{IsKatakana}]+)")
(def ^:private count-pattern-chars (map (comp count first)))
(defn- num-jp-chars [text]
  (->> (re-seq jp-regex text)
       (transduce count-pattern-chars +)))

(defn- preprocess [img]
  (preprocess-image img [scale grayscale invert-if-needed sharpen threshold add-white-margin]))

(defn- perform-orc [^BufferedImage img]
  (let [;; use the single character mode because this program is mostly intended for very short text
        single      (future (.doOCR (new-tesseract 10 "jpn") img))
        vertical?   (< (.getWidth img) (.getHeight img))
        ;; use the page segmentation as a backup in case the text is longer than expected
        multi-model (if vertical?
                      ;; use vertical segmentation if the image is higher than its width
                      (new-tesseract 5 "jpn_vert")
                      (new-tesseract 1 "jpn"))
        multi       (future (.doOCR multi-model img))]
    (log/info "single char:" @single)
    (log/info "words" (if vertical? "(vertical):" "(horizontal):") @multi)
    ;; choose the better model by counting the number of recognized japanese characters
    ;; if single and multi return the same number of chars, prefer single because it's more accurate
    ;; in my experience
    (max-key num-jp-chars @multi @single)))

(defn- split&filter-words [text]
  (->> text
       (re-seq jp-regex)
       (map first)
       (apply str)
       (segment (get-in config [:languages :jp :relevant-word-groups]))))

(defrecord Japanese []
  Language
  (get-ocr-text [_ img]
    (-> img
        (preprocess)
        (perform-orc)))
  (split-words [_ text]
    (split&filter-words text))
  (lookup [_ word]
    (lookup-word-in-dictionary word)))

(defn japanese []
  (Japanese.))
