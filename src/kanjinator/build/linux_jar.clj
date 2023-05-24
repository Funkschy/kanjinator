(ns kanjinator.build.linux-jar
  (:require [clojure.java.shell :refer [sh]]
            [clojure.edn :as edn]))

(def output-dir "target/default+uberjar")
(def jar-name (->> "project.clj"
                   (slurp)
                   (edn/read-string)
                   (rest)
                   (take 2)
                   (apply format "%s-%s-standalone.jar")))

(defn build-jar []
  (println "building uberjar")
  (sh "lein" "clean")
  (sh "lein" "uberjar")
  (sh "cp" (str output-dir "/" jar-name) "target/kanjinator-linux.jar")
  (println "removing windows stuff from target/kanjinator-linux.jar")
  (sh "zip" "-d" "target/kanjinator-linux.jar" "nu/pattern/opencv/windows/*")
  (sh "zip" "-d" "target/kanjinator-linux.jar" "nu/pattern/opencv/osx/*")
  (sh "zip" "-d" "target/kanjinator-linux.jar" "win32-x86-64/*")
  (sh "zip" "-d" "target/kanjinator-linux.jar" "win32-x86/*")
  (shutdown-agents))
