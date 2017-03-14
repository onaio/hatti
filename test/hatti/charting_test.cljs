(ns hatti.charting-test
  (:require-macros [cljs.test :refer [is deftest testing]])
  (:require [hatti.charting
             :refer [str->int parse-date parse-time evenly-spaced-bins]]
            [cljs-time.format :as tf]))

(def bin-size 5)

(deftest str->int-test
  (let [date->integer (str->int "date")]
    (testing "returns a binnable value when given a date in string form"
      (is (number? (date->integer "2015-01-12"))))
    (testing "returns a binnable value when given a time in string form"
      (is (parse-time "12:45") 1245))
    (testing "returns a binnable value when given a time in string form"
      (is (parse-time "8:45") 845))))

(deftest evenly-spaced-bins-test
  (testing "Returns bins of given size"
    (is (= (count
            (:bins (meta
                    (evenly-spaced-bins
                     ["15:05" "12:00" "11:00" "09:50" "11:10" "10:00" "15:00"
                      "12:00" "11:30" "16:45" "11:00" "15:00" "14:30" "14:30"
                      "11:00" "14:00" "08:00" "17:00" "10:00" "11:00" "14:30"
                      "17:00" "14:00" "11:00"]
                     bin-size
                     "time"))))
           bin-size))
    (is (=  (count
             (:bins (meta
                     (evenly-spaced-bins
                      ["16:05" "13:00" "12:00" "10:20" "12:10" "10:30" "16:00"
                       "13:00" "12:30" "18:20" "12:00" "16:00" "15:30" "15:30"
                       "12:00" "15:30" "09:00" "18:00" "11:00" "12:00" "15:30"
                       "18:00" "15:00" "12:00"]
                      bin-size
                      "time"))))
            bin-size))))
