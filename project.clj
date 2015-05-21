(defproject org.clojars.onaio/hatti-examples "0.1.0-SNAPSHOT"
  :description "Examples of using onaio/hatti"
  :dependencies [;; CORE REQUIREMENTS
                 [org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-3196"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [sablono "0.3.1"]
                 [org.omcljs/om "0.8.8"]
                 ;; HATTI
                 [org.clojars.onaio/hatti "0.1.0-SNAPSHOT"]
                 [onaio/milia "0.1.3-SNAPSHOT"]
                 ;; CLIENT REQUIREMENTS
                 [cljs-http "0.1.17"]]
  :plugins [[lein-cljsbuild "1.0.5"]]
  :source-paths ["examples/stolen/src"
                 "examples/osm/src"
                 "target/classes"]
  :clean-targets ["out/hatti" "out/hatti.js"]
  :cljx {:builds [{:source-paths ["src/cljx"]
                   :output-path "target/generated/src/clj"
                   :rules :clj}
                  {:source-paths ["src/cljx"]
                   :output-path "target/generated/src/cljs"
                   :rules :cljs}]}
  :cljsbuild {
    :builds [{:id "stolen"
              :source-paths ["examples/stolen/src" ]
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
              :source-paths [ "examples/osm/src" ]
              :compiler {
                  :output-to "examples/osm/main.js"
                  :output-dir "examples/osm/out"
                  :externs ["externs/leaflet-externs.js"
                            "externs/jquery-externs.js"
                            "externs/slickgrid-externs.js"]
                  :cache-analysis true
                  :optimizations :whitespace
                  :source-map "examples/osm/main.js.map"}}]})
