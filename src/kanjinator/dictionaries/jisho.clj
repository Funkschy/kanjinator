(ns kanjinator.dictionaries.jisho
  (:require
   [clojure.data.json :as json]
   [clojure.set :as s]
   [clojure.tools.logging :as log])
  (:import
   [java.io IOException]
   [java.net URLEncoder]
   [java.nio.charset StandardCharsets]))

(defn- encode-search-term [^String word]
  (URLEncoder/encode word StandardCharsets/UTF_8))

(defn- slurp-with-retry [tries url]
  (loop [n tries]
    (if-let [result (try
                      (slurp url)
                      (catch IOException e
                        (when (zero? n)
                          (throw e))))]
      result
      (do
        (log/info "Jisho call failed. Retrying" n "more times")
        (recur (dec n))))))

(def ^:private jisho-api-url "https://jisho.org/api/v1/search/words?keyword=")
(defn- get-from-jisho-api [word]
  (->> word
       encode-search-term
       (str jisho-api-url)
       (slurp-with-retry 3)))

(defn- select&rename [data-m key-m]
  (->> data-m
       (map #(select-keys % (keys key-m)))
       (map #(s/rename-keys % key-m))))

(defn- applies? [restrictions reading]
  (or (empty? restrictions)
      (restrictions reading)))

(defn- merge-applicable-meanings [restriction-map {reading :dict/reading :as reading-entry}]
  (reduce (fn [r [restrictions {:keys [definitions]}]]
            (cond-> r
              (applies? restrictions reading) (update :dict/meanings s/union (set definitions))))
          reading-entry
          restriction-map))

(def ^:private reading-keys {"word" :dict/writing "reading" :dict/reading})
(def ^:private meaning-keys {"english_definitions" :definitions "restrictions" :restrictions})

(defn- get-words-with-meanings [data-entry]
  (let [readings (select&rename (get data-entry "japanese") reading-keys)
        meanings (select&rename (get data-entry "senses") meaning-keys)
        restrict (-> (group-by :restrictions meanings)
                     (update-vals first)
                     (update-keys set))]
    (map (partial merge-applicable-meanings restrict) readings)))

(def ^:private homograph-slug-regex #"([\p{IsHan}\p{IsBopo}\p{IsHira}\p{IsKatakana}]+)(-\d+)?")
(defn- select-homograph
  "Some japanese words have the exact same kanji, but can still mean different things based on context.
   E.g. 生物 could mean 'せいぶつ' = 'living thing', or it could mean 'なまもの' = 'raw food'.
   The jisho.org api uses a <kanji>-<number> naming system for those homographs, so we group them
   based on that."
  [data]
  (let [groups (group-by (comp second
                               #(re-find homograph-slug-regex %)
                               #(get % "slug"))
                         data)
        most-important (->> (get-in data [0 "slug"] "")
                            (re-find homograph-slug-regex)
                            (second))]
    (groups most-important)))

(defn lookup-word-in-dictionary
  "Perform a dictionary lookup for the supplied word. This will return a valid :dict/entries"
  [word]
  (log/info "jisho lookup:" word)
  (let [data (-> (get-from-jisho-api word)
                 (json/read-str)
                 (get "data"))
        homograph (select-homograph data)
        meanings  (map get-words-with-meanings homograph)]
    (map (fn [meaning]
           {:dict/search-term word
            :dict/words meaning})
         meanings)))
