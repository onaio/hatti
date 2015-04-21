(def js-preamble
  ["META-INF/resources/webjars/SlickGrid/2.1/lib/jquery-1.7.min.js"
   "META-INF/resources/webjars/SlickGrid/2.1/lib/jquery.event.drag-2.2.js"
   "META-INF/resources/webjars/SlickGrid/2.1/slick.core.js"
   "META-INF/resources/webjars/SlickGrid/2.1/slick.grid.js"
   "META-INF/resources/webjars/SlickGrid/2.1/controls/slick.pager.js"
   "META-INF/resources/webjars/SlickGrid/2.1/slick.dataview.js"])

(defproject onaio/hatti "0.1.0-SNAPSHOT"
  :description "A cljs dataview from your friends at Ona.io"
  :dependencies [;; CORE HATTI REQUIREMENTS
                 [org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2843"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [sablono "0.3.1"]
                 [org.omcljs/om "0.8.8"]
                 [inflections "0.9.7"]
                 ;; JS REQUIREMENTS
                 [cljsjs/moment "2.9.0-0"]
                 [cljsjs/leaflet "0.7.3-0"]
                 ;; TODO: make into cljs packages
                 [org.webjars/SlickGrid "2.1"]
                 ;; CLIENT REQUIREMENTS
                 [cljs-http "0.1.17"]
                 [com.cognitect/transit-cljs "0.8.188"]]

  :plugins [[lein-cljsbuild "1.0.5"]]

  :source-paths ["src" "target/classes"]

  :clean-targets ["out/hatti" "out/hatti.js"]

  :cljsbuild {
    :builds [{:id "examples"
              :source-paths ["src" "examples/stolen/src"]
              :compiler {
                  :output-to "examples/stolen/main.js"
                  :output-dir "examples/stolen/out"
                  :preamble ~js-preamble
                  :cache-analysis true
                  :optimizations :advanced
                  :source-map "examples/stolen/main.js.map"}}
             {:id "hatti"
              :source-paths ["src"]
              :compiler {
                :output-to "out/hatti.js"
                :output-dir "out"
                :optimizations :none
                :cache-analysis true
                :source-map true}}]})
