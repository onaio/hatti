(ns hatti.style-test
  (:require [clojure.java.shell :refer [sh]]
            [midje.sweet :refer :all]))

(fact "It shall pass the kibit static analysis checks"
      (:out (sh "lein" "kibit")) => "")

(fact "It shall pass the bikeshed checks"
      (sh "lein" "bikeshed") => (contains {:exit 0}))
