(ns kanjinator.language)

(defprotocol Language
  (get-ocr-words [this img]
    "Perform OCR on the image and then split the text into words which should be looked up.
     This should only return the words which should be looked up in the dictionary")
  (lookup [this word]
    "Look up a single word in the dictionary for this language.
     The result should be a valid :dict/entries list"))
