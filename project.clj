(defproject kanjinator "0.2.4"
  :description "A simple tool to translate Japanese words on your screen"
  :license {:name "MIT"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [org.clojure/data.json "2.4.0"]
                 [org.clojure/tools.logging "1.2.4"]
                 [net.sourceforge.tess4j/tess4j "5.2.0"]
                 [org.openpnp/opencv "4.5.1-2"]
                 [com.atilika.kuromoji/kuromoji-ipadic "0.9.0"]]
  :main ^:skip-aot kanjinator.core
  :target-path "target/%s"
  :global-vars {*warn-on-reflection* true}
  :jvm-opts ["-Dclojure.tools.logging.factory=kanjinator.logging/logger-factory"]
  :aliases {"build-linux" ["run" "-m" "kanjinator.build.linux-jar/build-jar"]
            "build-windows" ["run" "-m" "kanjinator.build.windows-msi/build-msi"]}
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})
