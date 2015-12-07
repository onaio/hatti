(ns hatti.cljsbuild-test
  (:require [clojure.java.shell :refer [sh]]
            [midje.sweet :refer :all]))

(fact "It shall combile the CLJS without error"
      (re-find #"ERROR" (:out (sh "lein" "cljsbuild" "once" "prod"))) => nil)
