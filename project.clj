(defproject org.clojars.onaio/hatti "0.2-SNAPSHOT"
  :description "A cljs dataview from your friends at Ona.io"
  :license "Apache 2, see LICENSE"
  :url "https://github.com/onaio/hatti"
  :dependencies [ ;; CORE HATTI REQUIREMENTS
                 [org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "0.0-3308"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [sablono "0.3.1"]
                 [org.omcljs/om "0.8.8"]
                 [inflections "0.9.7"]
                 [secretary "1.2.3"]
                 ;; FOR CHARTS
                 [com.keminglabs/c2 "0.2.4-SNAPSHOT"]
                 [org.clojure/math.numeric-tower "0.0.4"]
                 [clj-time "0.7.0"]
                 [com.andrewmcveigh/cljs-time "0.2.3"]
                 ;; JS REQUIREMENTS
                 [cljsjs/moment "2.9.0-1"]
                 [onaio/leaflet-cljs "0.7.3-SNAPSHOT"]
                 [cljsjs/oboe "2.1.2-1"]
                 [cljsjs/jquery "1.9.1-0"]
                 [prabhasp/slickgrid-cljs "0.0.1"]
                 [prabhasp/osmtogeojson-cljs "2.2.5-1"]
                 ;; TODO: make into cljs packages
                 [org.webjars/SlickGrid "2.1"]
                 ;; CLIENT REQUIREMENTS
                 [prismatic/dommy "0.1.2"]
                 [cljs-http "0.1.17"]]
  :source-paths ["src"]
  :plugins [[lein-cljsbuild "1.1.0"]]
  :clean-targets ["out/hatti" "out/hatti.js"]
  :cljsbuild
  {:builds [{:id "hatti"
             :compiler {:output-to "out/hatti.js"
                        :output-dir "out"
                        :optimizations :none
                        :cache-analysis true
                        :source-map true}}
            {:id "test"
             :source-paths ["src/hatti"
                            "test"]
             :notify-command ["phantomjs"
                              "phantom/unit-test.js"
                              "phantom/unit-test.html"
                              "target/main-test.js"]
             :compiler {:output-to "target/main-test.js"
                        :optimizations :whitespace
                        :pretty-print true}}]
   :test-commands {"unit-test"
                   ["phantomjs"
                    "phantom/unit-test.js"
                    "phantom/unit-test.html"
                    "target/main-test.js"]}})
