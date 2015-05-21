(ns ona.dataview.single-submission-test
  (:require-macros [cljs.test :refer (is deftest testing)]
                   [dommy.macros :refer [node sel sel1]])
  (:require [cljs.test :as t]
            [cljs.core.async :refer [<! chan put!]]
            [dommy.core :as dommy]
            [ona.dataview.base :as dv]
            [ona.dataview.map :as mv]
            [ona.dataview.single-submission :as ss]
            [ona.utils.dom :refer [new-container!]]
            [ona.utils.forms :as f]
            [ona.helpers.permissions :refer [owner readonly]]
            [om.core :as om :include-macros true]
            [ona.utils.seq :refer [diff]]
            [ona.dataview.shared-test :refer [fat-form small-fat-data]]))

;; MAP COMPONENT HELPERS

(def event-chan (chan))
(def data small-fat-data)
(def gps-field {:full-name "gps" :name "gps" :type "gps" :label "GPS"})
(def gps-form (conj fat-form gps-field))

(defn- submission-container
  [map-or-table data form role & [more-args]]
  "Returns a container in which a map component has been rendered.
   `data` arg is directly passed into the component as its cursor."
  (let [c (new-container!)
        args {:shared {:flat-form form
                       :event-chan event-chan}
              :opts {:role role}
              :target c}
        _ (om/root (ss/submission-view map-or-table)
                   {:data data
                    :geofield gps-field
                    :dataset-info {:metadata [{:xform 1
                                               :data_value "tutorial_xlsform.xlsx|https://j2x.ona.io/xls/123"
                                               :data_type "external_export"}]}}
                   (merge args more-args))]
    c))

;; MAP COMPONENT TESTS

(deftest submission-legend-renders-properly
  (let [d (merge (first data)
                 {"_geolocation" [1.2, 2.1] "gps" "2.1 1.2 0 0"})
        meta-answers (vals (select-keys d (map :name (f/meta-fields gps-form))))
        qstn-answers (vals (select-keys d (map :name (f/non-meta-fields gps-form))))
        s (submission-container :map d gps-form owner)
        es (submission-container :map d gps-form owner {:init-state
                                               {:expand-meta? true}})
        expand-meta #(sel1 % :.expand-meta)]
    (testing "Submission XX title is present"
      (is (re-matches #".*Submission 1.*" (dommy/text (sel1 s :h4)))))
    (testing "Each Question / Answer pair is rendered"
      (is (every? (->> (sel s :span.answer) (map dommy/text) set)
                  qstn-answers))
      (is (every? (->> (sel s :span.question) (map dommy/text) set)
                  (map :label (f/non-meta-fields gps-form)))))
    (testing "Show data link is present and worded correctly"
      (is (= (dommy/text (expand-meta s)) "Show Metadata"))
      (is (= (dommy/text (expand-meta es)) "Hide Metadata")))
    (testing "When metadata expanded, each metadata q / a should be there"
      (is (every? (->> (sel es :span.answer) (map dommy/text) set)
                  meta-answers))
      (is (every? (->> (sel es :span.question) (map dommy/text) set)
                  (map :label (f/meta-fields gps-form)))))
    (testing "Print xls report button renders when reports exist"
      (is (sel1 :#print-xls-report)))))

(deftest repeat-rendering
  (let [form [{:name "rpt" :full-name "rpt" :type "repeat" :label "RPT"
               :children gps-form}]
        repeat-data (for [datum data]
                      (apply merge (for [[k v] datum]
                                     {(str "rpt/" k) v})))
        c1 (new-container!)
        c2 (new-container!)]
    (testing "repeats render as collapses in the beginning by default"
      (om/root ss/repeat-view
               {:data repeat-data :repeat-field form}
               {:target c1 :opts {:view :map}})
      (is (not (sel1 :ol c1)))
      (is (= (str (count repeat-data)) (re-find #"[0-9]+" (dommy/text c1))))
      (is (re-find #"Show Repeats" (dommy/text c1))))
    (testing "when uncollapsed, repeat render internal data"
      (om/root ss/repeat-view
               {:data repeat-data :repeat-field form}
               {:target c2 :opts {:view :map} :init-state {:collapsed? false}})
      (is (sel1 c2 :ol.repeat))
      (is (= (count data)
             (count (-> c2 (sel1 :ol.repeat) (sel :li)))))
      (is (re-find #"Hide Repeats" (dommy/text c2))))))

(deftest map-vs-table-views-are-different
  (let [d (first data)
        meta-answers (vals (select-keys d (map :name (f/meta-fields gps-form))))
        qstn-answers (vals (select-keys d (map :name (f/non-meta-fields gps-form))))
        map-el (submission-container :map d gps-form owner)
        tbl-el (submission-container :table d gps-form owner)
        submission-time (get d "_submission_time")]
    (testing "Submission XX title is present for both"
      (is (re-find #"Submission 1" (dommy/text (sel1 map-el :h4))))
      (is (re-find #"Submission 1" (dommy/text (sel1 tbl-el :h4)))))
    (testing "Map has a div.info-scroll, table has a table"
      (is (sel1 map-el :div.info-scroll))
      (is (sel1 tbl-el :table)))
    (testing "'No geodata' text only appears on the map submission view"
      (is (re-find #"No geodata" (dommy/text map-el)))
      (is (not (re-find #"No geodata" (dommy/text tbl-el)))))))

(deftest no-geodata
  (let [d-gps (merge (first data) {"gps" "2.1 1.2 0 0"})
        d-no-gps (merge (first data) {"gps" nil})
        map-el-no-geo (submission-container :map d-no-gps gps-form owner)
        map-el-yes-geo (submission-container :map d-gps gps-form owner)]
    (testing "'No geodata' only appears if geodata field is missing."
      (is (re-find #"No geodata" (dommy/text map-el-no-geo)))
      (is (not (re-find #"No geodata" (dommy/text map-el-yes-geo)))))))

