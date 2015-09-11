(ns hatti.views.dataview-test
  (:require-macros [cljs.test :refer (is deftest testing)]
                   [dommy.macros :refer [sel sel1]])
  (:require [cljs.test :as t]
            [dommy.core :as dommy]
            [hatti.shared :as shared]
            [hatti.utils :refer [in?]]
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
    (testing "Map tab is not rendered when there is no geodata"
      (is (= "map"
             (-> tabbed-view (sel1 :div.tab-bar) (sel1 :.inactive) (dommy/html)))))))

(deftest disabled-tabbed-tests
  (testing "Map, Chart and Table tabs are disabled"
    (let [disabled-views [:map :table :chart]
          data-atom (shared/empty-app-state)
          _ (shared/transact-app-state! data-atom
                                        [:views :disabled]
                                        #(identity disabled-views))
          tabbed-view (tabbed-dataview-container data-atom)]
      (doseq [tab (-> tabbed-view (sel1 :div.tab-bar) (sel :.inactive))]
        (is (in? disabled-views
                 (keyword (dommy/html tab))))))))
