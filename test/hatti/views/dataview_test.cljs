(ns hatti.views.dataview-test
  (:require-macros [cljs.test :refer (is deftest testing)]
                   [dommy.macros :refer [sel1]])
  (:require [cljs.test :as t]
            [dommy.core :as dommy]
            [hatti.shared :as shared]
            [hatti.views :refer [tabbed-dataview]]
            [hatti.test-utils :refer [big-thin-data
                                      data-gen
                                      format
                                      new-container!
                                      thin-form]]
            [om.core :as om :include-macros true]))

;; INITIAL STATE TESTS
(deftest initial-state
  (let [_ (shared/update-app-data! shared/app-state big-thin-data :rerank? true)
        data-100 (get-in @shared/app-state [:data])
        dget (fn [k d] (map #(get % k) d))]
    (testing "data is initialized properly"
      (is (= (-> data-100 first count) (-> big-thin-data first count)))
      (is (= 100 (count data-100))))
    (testing "data has correct _rank attributes"
      (is (= (dget "_rank" data-100) (map inc (range 100)))))
    (testing "data is in sorted order by _submission_time"
      (let [test-dates (map #(.format (js/moment %))
                            ["2012-01-01" "2013-01-01" "2011-01-01"])
            s100 (map #(.format (js/moment %))
                      (dget "_submission_time" data-100))]
        (is (= (sort test-dates) (conj (take 2 test-dates) (last test-dates))))
        (is (= s100 (sort s100)))))))


;;TABBED DATAVIEW TESTS
(defn- tabbed-dataview-container
  [app-state]
  (let [c (new-container!)
        _ (om/root tabbed-dataview
                   app-state
                   {:shared {:flat-form thin-form
                             :project-id "1"
                             :project-name "Project"
                             :dataset-id "2"
                             :username "user"
                             :view-type :default}
                    :target c})]
    c))

(deftest tabbed-dataview-tests
  (let [tabbed-view (tabbed-dataview-container shared/app-state)]
    (testing "All tabs are rendered when none is disabled"
      (is (re-find #"inactive" (dommy/html (sel1 tabbed-view)))))))
