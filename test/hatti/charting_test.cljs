(ns hatti.charting-test
  (:require-macros [cljs.test :refer [is deftest testing]])
  (:require [hatti.charting :refer [str->int]]))

(deftest str->int-test
  (let [date->integer (str->int "date")]
    (testing "returns a binnable value when given a date in string form"
      (is (number? (date->integer "2015-01-12"))))))
