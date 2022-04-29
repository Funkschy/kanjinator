(ns kanjinator.ocr
  (:import
   (java.awt.image BufferedImage)
   (java.awt.image BufferedImage)
   (net.sourceforge.tess4j Tesseract)))

(def ^Tesseract tesseract
  (doto (new Tesseract)
    (.setDatapath "resources/tessdata")
    (.setLanguage "jpn")
    ;; use the single character mode because this program is mostly intended for very short text
    ;; TODO: use both 10 and 1 and then pick the one with more japanese characters
    (.setPageSegMode 10)
    (.setOcrEngineMode 1)))

(defn perform-ocr [^BufferedImage img]
  (.doOCR tesseract img))
