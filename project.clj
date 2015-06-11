(defproject org.clojars.onaio/hatti "0.1.1-SNAPSHOT"
  :description "A cljs dataview from your friends at Ona.io"
  :dependencies [;; CORE HATTI REQUIREMENTS
                 [org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-3196"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [sablono "0.3.1"]
                 [org.omcljs/om "0.8.8"]
                 [inflections "0.9.7"]
                 [secretary "1.2.3"]
                 ;; CLJX
                 [com.keminglabs/cljx "0.6.0" :exclusions [org.clojure/clojure]]
                 ;; FOR CHARTS
                 [com.keminglabs/c2 "0.2.4-SNAPSHOT"]
                 [org.clojure/math.numeric-tower "0.0.4"]
                 [clj-time "0.7.0"]
                 [com.andrewmcveigh/cljs-time "0.2.3"]
                 ;; JS REQUIREMENTS
                 [cljsjs/moment "2.9.0-0"]
                 [prabhasp/leaflet-cljs "0.7.3"]
                 [cljsjs/jquery "1.9.1-0"]
                 [prabhasp/slickgrid-cljs "0.0.1"]
                 [prabhasp/osmtogeojson-cljs "2.2.5-1"]
                 ;; TODO: make into cljs packages
                 [org.webjars/SlickGrid "2.1"]
                 ;; CLIENT REQUIREMENTS
                 [cljs-http "0.1.17"]]
  :plugins [[lein-cljsbuild "1.0.5"]
            [com.keminglabs/cljx "0.6.0" :exclusions [org.clojure/clojure]]]
  :source-paths ["src/cljs"
                 "target/generated/src/clj"
                 "target/generated/src/cljs"
                 "target/classes"]
  :clean-targets ["out/hatti" "out/hatti.js"]
  :prep-tasks [["cljx" "once"] "javac" "compile"]
  :cljx {:builds [{:source-paths ["src/cljx"]
                   :output-path "target/generated/src/clj"
                   :rules :clj}
                  {:source-paths ["src/cljx"]
                   :output-path "target/generated/src/cljs"
                   :rules :cljs}]}
  :cljsbuild {
    :builds [{:id "stolen"
              :source-paths ["src/cljs"
                             "target/generated/src/cljs"
                             "examples/stolen/src" ]
              :compiler {
                  :output-to "examples/stolen/main.js"
                  :output-dir "examples/stolen/out"
                  :externs ["includes/externs/leaflet-externs.js"
                            "includes/externs/jquery-externs.js"
                            "includes/externs/slickgrid-externs.js"]
                  :cache-analysis true
                  :optimizations :whitespace
                  :source-map "examples/stolen/main.js.map"}}
             {:id "osm"
              :source-paths ["src/cljs"
                             "target/generated/src/cljs"
                             "examples/osm/src" ]
              :compiler {
                  :output-to "examples/osm/main.js"
                  :output-dir "examples/osm/out"
                  :externs ["externs/leaflet-externs.js"
                            "externs/jquery-externs.js"
                            "externs/slickgrid-externs.js"]
                  :cache-analysis true
                  :optimizations :whitespace
                  :source-map "examples/osm/main.js.map"}}
             {:id "hatti"
              :source-paths ["src/cljs"
                             "target/generated/src/cljs"]
              :compiler {
                :output-to "out/hatti.js"
                :output-dir "out"
                :optimizations :none
                :cache-analysis true
                :source-map true}}]})
