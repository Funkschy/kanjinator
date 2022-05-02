(ns kanjinator.core
  (:gen-class)
  (:require
   [kanjinator.dictionaries.spec]
   [kanjinator.languages.jp]
   [kanjinator.ui :refer [run-application-window]])
  (:import
   [nu.pattern OpenCV]))

(defn -main []
  (OpenCV/loadLocally)
  (run-application-window))
