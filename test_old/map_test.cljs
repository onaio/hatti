(ns ona.dataview.map-test
  (:require-macros [cljs.test :refer (is deftest testing)]
                   [dommy.macros :refer [node sel sel1]])
  (:require [cljs.test :as t]
            [cljs.core.async :refer [<! chan put!]]
            [dommy.core :as dommy]
            [ona.dataview.shared :refer [app-state]]
            [ona.dataview.base :as dv]
            [ona.dataview.map :as mv]
            [ona.dataview.map-utils :as mu]
            [ona.dataview.map-viewby :as vb]
            [ona.dataview.dommy-helpers :as dh]
            [ona.utils.dom :refer [new-container!]]
            [ona.utils.forms :as f]
            [ona.helpers.permissions :refer [owner readonly]]
            [om.core :as om :include-macros true]
            [ona.utils.seq :refer [diff]]
            [ona.dataview.shared-test :refer [fat-form no-data small-fat-data data-gen]]))

;; MAP COMPONENT HELPERS

(def event-chan (chan))
(def map-data (data-gen 4 50))
(def map-form (conj (take 4 fat-form)
                    {:type "geopoint" :label "GPS"
                     :name "gps" :full-name "gps"}))
(def geojson (mu/as-geojson map-data map-form))

(defn- map-container
  [component cmp-data form role & [more-args]]
  "Returns a container in which a map component has been rendered.
   `data` arg is directly passed into the component as its cursor."
  (let [c (new-container!)
        args {:shared {:flat-form form :event-chan event-chan}
              :opts {:role role :geojson geojson}}
        _ (om/root component cmp-data (merge args more-args {:target c}))]
    c))

;; MAP COMPONENT TESTS

(deftest viewby-menu-renders-properly
  (let [sel1s (filter f/select-one? map-form)
        nodata {:view-by [] :dataset-info {:num_of_submissions 0}}
        somedata {:view-by [] :dataset-info {:num_of_submissions 100}}
        viewby-nodata (map-container mv/view-by-legend nodata map-form owner)
        viewby (map-container mv/view-by-legend somedata map-form owner)
        menu (sel1 viewby :ul.submenu)
        lis (sel menu :li)]
    (testing "viewby menu renders 'No data' when there is no data"
      (is (re-find #"No data" (dommy/html viewby-nodata))))
    (testing "viewby menu renders labels from all select one fields"
      (is (= (map :label sel1s) (dh/texts lis))))))

(deftest viewby-answer-renders-properly
  (let [sel1-field {:type "select one" :name "name"
                    :full-name "Name" :label "This is the label"
                    :children [{:name "1" :label "One"}
                               {:name "2" :label "Two"}]}
        vbdata {:view-by (vb/viewby-info sel1-field ["1" "2" "2" nil nil nil] (range 7))
                :dataset-info {:num_of_submissions 100}}
        viewby (map-container mv/view-by-legend vbdata map-form owner)
        option-list (sel viewby :li)]
    (testing "viewby-answer renders properly without selections"
      (is (re-find #"This is the label" (dommy/text viewby)))
      (is (re-find (js/RegExp. f/no-answer) (apply str (dh/texts option-list))))
      (is (re-find #"One" (apply str (dh/texts option-list))))
      (is (re-find #"Two" (apply str (dh/texts option-list)))))
    (testing "viewby-answers render with No Answer at bottom and grey"
      (is (re-find (js/RegExp. f/no-answer) (dommy/text (last option-list))))
      (is (= (str vb/grey ";") (-> (last option-list)
                                (sel1 :div.small-circle)
                                (dommy/attr "style")
                                (clojure.string/split ":")
                                last))))))

(deftest geofield-chooser-renders-properly
  (let [geofields [{:type "geoshape" :name "geoshape"
                    :full-name "geoshape" :label "Geo Shape"}
                   {:type "geopoint" :name "geopoint"
                    :full-name "geopoint" :label "Geo Point"}]
        geo-c (map-container mv/geofield-chooser
                             (last geofields)
                             map-form owner
                             {:opts {:geofields geofields}})]
    (testing "geofield-chooser renders all geo fields"
      (is (= (dommy/text geo-c) (apply str (map :label geofields)))))
    (testing "radio corresponding to geofield is selected"
      (is (-> (sel :input) last (dommy/attr :checked))))))
