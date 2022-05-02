(ns kanjinator.languages.jp
  (:require
   [kanjinator.language :refer [Language]]
   [kanjinator.preprocess :refer [default-process-pipeline preprocess-image]]
   [kanjinator.dictionaries.jisho :refer [lookup-word-in-dictionary]])
  (:import
   [com.atilika.kuromoji.ipadic Token Tokenizer]
   [java.awt.image BufferedImage]
   [net.sourceforge.tess4j Tesseract]))

(def ^:private parts-of-speech-1
  {"名詞"   :noun           ;; めいし
   "助詞"   :particle       ;; じょし
   "形容詞" :adjective      ;; けいようし
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
  (let [tokens (.tokenize (new Tokenizer) text)
        base   (fn [^Token t] (.getBaseForm t))
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

(def ^:private ^Tesseract tesseract-single
  (doto (new Tesseract)
    (.setDatapath "resources/tessdata")
    (.setLanguage "jpn")
    ;; use the single character mode because this program is mostly intended for very short text
    (.setPageSegMode 10)
    (.setOcrEngineMode 1)))

(def ^:private ^Tesseract tesseract-multi
  (doto (new Tesseract)
    (.setDatapath "resources/tessdata")
    (.setLanguage "jpn")
    ;; use the page segmentation as a backup incase the text is longer than expected
    (.setPageSegMode 1)
    (.setOcrEngineMode 1)))

(def ^:private jp-regex #"([\p{IsHan}\p{IsBopo}\p{IsHira}\p{IsKatakana}]+)")
(def ^:private count-pattern-chars (map (comp count first)))
(defn- num-jp-chars [text]
  (->> (re-seq jp-regex text)
       (transduce count-pattern-chars +)))

(defrecord Japanese []
  Language
  (perform-ocr [_ img]
    (let [img    (preprocess-image img default-process-pipeline)
          single (future (.doOCR tesseract-single ^BufferedImage img))
          multi  (future (.doOCR tesseract-multi ^BufferedImage img))]
      ;; choose the better model by counting the number of recognized japanese characters
      (max-key num-jp-chars @single @multi)))
  (split-words [_ text]
    (->> text
         (re-seq jp-regex)
         (map first)
         (apply str)
         (segment #{:noun :verb :adjective})))
  (lookup [_ word]
    (lookup-word-in-dictionary word)))

(defn japanese []
  (Japanese.))
