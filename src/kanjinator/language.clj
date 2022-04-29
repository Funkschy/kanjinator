(ns kanjinator.language)

(defprotocol Language
  (perform-ocr [this img]
    "Takes a language and a BufferedImage and returns the text contained inside")
  (split-words [this text]
    "Split the text into words which should be looked up.
     This should only return the words which should be looked up in the dictionary"))
