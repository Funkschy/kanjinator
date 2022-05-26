(ns kanjinator.build.windows-msi
  (:require [clojure.java.shell :refer [sh]]
            [clojure.edn :as edn]))

(def output-dir "target\\uberjar")
(def jar-name (->> "project.clj"
                   (slurp)
                   (edn/read-string)
                   (rest)
                   (take 2)
                   (apply format "%s-%s-standalone.jar")))

(defn build-msi []
  (println "building uberjar")
  (sh "lein.bat" "clean")
  (sh "lein.bat" "uberjar")

  (println "moving jar")
  (sh "cmd" "/c" "copy" (str output-dir "\\" jar-name) "target\\kanjinator-windows.jar")

  (println "packaging windows msi file")
  (sh "jpackage.exe"
      "--input" "target"
      "--type" "msi"
      "--win-dir-chooser"
      "--win-shortcut"
      "--win-menu-group" "Productivity"
      "--main-jar" "kanjinator-windows.jar"
      "--add-modules" "java.sql,java.datatransfer,java.desktop,java.logging,java.prefs,java.xml,jdk.unsupported"
      "--description" "Like yomichan, but outside the browser"
      "--name" "Kanjinator"
      "--license-file" ".\\LICENSE"
      "--resource-dir" "resources"
      "--icon" "resources\\kanjinator.ico")
  (shutdown-agents))
