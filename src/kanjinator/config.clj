(ns kanjinator.config
  (:require
   [clojure.string :as str]))

(def windows? (-> (System/getProperty "os.name")
                  (str/lower-case)
                  (str/includes? "windows")))

(def config
  {:log {}
   :current-language 'kanjinator.languages.jp/japanese
   :languages
   {:jp
    {:relevant-word-groups #{:noun :verb :adjective}}}})
