(ns kanjinator.core
  (:gen-class)
  (:require
   [kanjinator.clipboard :refer [read-primary-clipboard
                                 read-secondary-clipboard]]
   [kanjinator.config :as config]
   [kanjinator.dictionaries.spec]
   [kanjinator.language :refer [lookup split-words]]
   [kanjinator.languages.jp]
   [kanjinator.ui :refer [display-results-no-existing-window
                          run-application-window]])
  (:import
   [nu.pattern OpenCV]))

(defn- display-results-without-ocr [text]
  (let [language ((resolve (get config/config :current-language)))]
    (->> text
         (split-words language)
         (mapcat (partial lookup language))
         (display-results-no-existing-window text))))

(defn -main [& args]
  (condp #(contains? %2 %1) (set args)
    "-c1" (display-results-without-ocr (read-primary-clipboard))
    "-c2" (display-results-without-ocr (read-secondary-clipboard))
    (run-application-window (future (OpenCV/loadLocally)))))
