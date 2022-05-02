(ns kanjinator.config)

(def config
  {:log {}
   :current-language 'kanjinator.languages.jp/japanese
   :languages
   {:jp
    {:relevant-word-groups #{:noun :verb :adjective}}}})
