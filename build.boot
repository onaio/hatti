(set-env! :source-paths   #{"src"}
          :resource-paths #{"src"}
          :dependencies
          '[ ;; CORE HATTI REQUIREMENTS
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
            [cljsjs/moment "2.9.0-0"]
            [prabhasp/leaflet-cljs "0.7.3"]
            [cljsjs/jquery "1.9.1-0"]
            [prabhasp/slickgrid-cljs "0.0.1"]
            [prabhasp/osmtogeojson-cljs "2.2.5-1"]
            ;; TODO: make into cljs packages
            [org.webjars/SlickGrid "2.1"]
            ;; CLIENT REQUIREMENTS
            [cljs-http "0.1.17"]

            ;; boot tool chain
            [adzerk/boot-cljs "0.0-3308-0"]
            [modnakasta/boot-cljs-repl  "0.1.10-SNAPSHOT" :scope "test"]
            [com.cemerick/piggieback "0.2.1"              :scope "test"]
            [org.clojure/tools.nrepl "0.2.10"             :scope "test"]

            [adzerk/boot-reload         "0.3.1"           :scope "test"]
            [boot-cljs-test/node-runner "0.1.0"           :scope "test"]
            [pandeiro/boot-http "0.6.3-SNAPSHOT"          :scope "test"]])

(require '[adzerk.boot-cljs :refer :all])

(task-options! pom {:project 'org.clojars.onaio/hatti
                    :version "0.1.5-SNAPSHOT"})

(deftask build
  []
  (comp (pom) (jar) (install)))

(deftask dev-build
  []
  (comp (watch) (build)))
