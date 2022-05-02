(defproject kanjinator "0.1.0-SNAPSHOT"
  :description "A simple tool to translate Japanese words on your screen"
  :license {:name "MIT"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [net.sourceforge.tess4j/tess4j "5.2.0"]
                 [org.openpnp/opencv "4.5.1-2"]
                 [org.clojure/data.json "2.4.0"]
                 [com.atilika.kuromoji/kuromoji-ipadic "0.9.0"]
                 [com.github.funkschy/tinysegmenter "0.1.0"]]
  :main ^:skip-aot kanjinator.core
  :target-path "target/%s"
  :global-vars {*warn-on-reflection* true}
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})
