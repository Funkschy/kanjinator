(ns kanjinator.dictionaries.spec
  (:require
   [clojure.spec.alpha :as s]))

(s/def :dict/search-term string?)
(s/def :dict/reading string?)
(s/def :dict/writing string?)
(s/def :dict/meanings (s/coll-of string? :kind set?))
(s/def :dict/example-sentence string?)
(s/def :dict/examples (s/* :dict/example-sentence))

(s/def :dict/word (s/keys :req [:dict/writing :dict/reading :dict/meanings]
                          :opt [:dict/examples]))
(s/def :dict/words (s/* :dict/word))

(s/def :dict/entry (s/keys :req [:dict/search-term :dict/words]))
(s/def :dict/entries (s/* :dict/entry))
