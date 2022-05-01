(ns kanjinator.languages.jp
  (:require
   [kanjinator.language :refer [Language]]
   [kanjinator.preprocess :refer [default-process-pipeline preprocess-image]]
   [tinysegmenter.core :refer [segment]]
   [kanjinator.dictionaries.jisho :refer [lookup-word-in-dictionary]])
  (:import
   [java.awt.image BufferedImage]
   [net.sourceforge.tess4j Tesseract]))

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
         (segment)))
  (lookup [_ word]
    (lookup-word-in-dictionary word)))

(defn japanese []
  (Japanese.))
