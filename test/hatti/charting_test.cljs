(ns hatti.charting-test
  (:require-macros [cljs.test :refer [is deftest testing]])
  (:require [hatti.charting :refer [str->int parse-date parse-time evenly-spaced-bins]]
            [cljs-time.format :as tf]))

(deftest str->int-test
  (let [date->integer (str->int "date")]
    (testing "returns a binnable value when given a date in string form"
      (is (number? (date->integer "2015-01-12"))))
    (testing "returns a binnable value when given a time in string form"
      (is (parse-time "12:45") 1245))
    (testing "returns a binnable value when given a time in string form"
      (is (parse-time "8:45") 845))))

(deftest evenly-spaced-bins-test
  (testing ""
    (is (= (meta  (evenly-spaced-bins
                   ["15:05" "12:00" "11:00" "09:50" "11:10" "10:00" "15:00"
                    "12:00" "11:30" "16:45" "11:00" "15:00" "14:30" "14:30"
                    "11:00" "14:00" "08:00" "17:00" "10:00" "11:00" "14:30"
                    "17:00" "14:00" "11:00"]
                   5
                   "time"))
           {:bins ["8:00 to 9:79" "9:80 to 11:59" "11:60 to 13:39"
                   "13:40 to 15:19" "15:20 to 17:00"]}))
    (is (=  (meta (evenly-spaced-bins
                   ["16:05" "13:00" "12:00" "10:20" "12:10" "10:30" "16:00"
                    "13:00" "12:30" "18:20" "12:00" "16:00" "15:30" "15:30"
                    "12:00" "15:30" "09:00" "18:00" "11:00" "12:00" "15:30"
                    "18:00" "15:00" "12:00"]
                   5
                   "time"))
            {:bins ["9:00 to 10:83" "10:84 to 12:67" "12:68 to 14:51"
                    "14:52 to 16:35" "16:36 to 18:20"]}))))
