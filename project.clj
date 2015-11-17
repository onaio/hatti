(defproject onaio/hatti "0.1.13-SNAPSHOT"
  :description "A cljs dataview from your friends at Ona.io"
  :license "Apache 2, see LICENSE"
  :url "https://github.com/onaio/hatti"
  :dependencies [;; Core libraries
                 [org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "1.7.145"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [sablono "0.3.1"]
                 [org.omcljs/om "0.8.8"]
                 [inflections "0.9.7"]
                 [secretary "1.2.3"]
                 ;; For charts
                 [com.keminglabs/c2 "0.2.4-SNAPSHOT"]
                 [org.clojure/math.numeric-tower "0.0.4"]
                 [clj-time "0.7.0"]
                 [com.andrewmcveigh/cljs-time "0.2.3"]
                 ;; JS
                 [cljsjs/moment "2.9.0-1"]
                 [onaio/leaflet-cljs "0.7.3-SNAPSHOT"]
                 [cljsjs/oboe "2.1.2-1"]
                 [cljsjs/jquery "1.9.1-0"]
                 [onaio/slickgrid-cljs "0.0.3-SNAPSHOT"]
                 [prabhasp/osmtogeojson-cljs "2.2.5-1"]
                 [org.webjars/SlickGrid "2.1"]
                 ;; For client
                 [cljs-http "0.1.17"]
                 ;; For testing
                 [prismatic/dommy "1.1.0"]]
  :source-paths ["src"]
  :plugins [[lein-cljsbuild "1.1.0"]]
  :clean-targets ["out/hatti" "out/hatti.js"]
  :cljsbuild
  {:builds
   [{:builds
     [{:id "hatti"
       :compiler
       {:output-dir "out"
        :cache-analysis true
        :optimizations :none
        :output-to "out/hatti.js"
        :source-map true}}
      {:notify-command
       ["phantomjs"
        "phantom/unit-test.js"
        "phantom/unit-test.html"
        "target/main-test.js"]
       :source-paths ["src/hatti" "test"]
       :id "test"
       :compiler
       {:optimizations :whitespace,
        :output-to "target/main-test.js"
        :pretty-print true}}]
     :test-commands
     {"unit-test"
      ["phantomjs"
       "phantom/unit-test.js"
       "phantom/unit-test.html"
       "target/main-test.js"]}
     :compiler {:exclude ["charting.cljc" "macros.clj"]}}]})
