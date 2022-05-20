(ns kanjinator.core
  (:gen-class)
  (:require
   [kanjinator.dictionaries.spec]
   [kanjinator.languages.jp]
   [kanjinator.ui :refer [run-application-window]])
  (:import
   [nu.pattern OpenCV]))

(defn -main []
  (run-application-window (future (OpenCV/loadLocally))))
