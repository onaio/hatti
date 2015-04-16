(defproject hatti "0.1.0-SNAPSHOT"
  :description "A cljs dataview from your friends at Ona.io"

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2755"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [org.omcljs/om "0.8.8"]]

  :plugins [[lein-cljsbuild "1.0.4"]]

  :source-paths ["src" "target/classes"]

  :clean-targets ["out/hatti" "out/hatti.js"]

  :cljsbuild {
    :builds [{:id "hatti"
              :source-paths ["src"]
              :compiler {
                :output-to "out/hatti.js"
                :output-dir "out"
                :optimizations :none
                :cache-analysis true
                :source-map true}}]})
