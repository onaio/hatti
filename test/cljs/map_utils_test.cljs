(ns ona.dataview.map-utils-test
  (:require-macros [cljs.test :refer (is deftest testing)]
                   [dommy.macros :refer [sel1 sel]]
                   [ona.utils.macros :refer [read-file]])
  (:require [cljs.test :as t]
            [cljs.core.async :refer [chan]]
            [clojure.string :as s]
            [dommy.core :as dommy]
            [ona.utils.dom :as domutils]
            [ona.utils.interop :refer [json->js->cljs json->cljs]]
            [ona.utils.forms :as f]
            [ona.dataview.shared :as shared]
            [ona.dataview.dommy-helpers :as dh]
            [ona.dataview.map-utils :as mu]
            [ona.dataview.osm-utils-test :refer [osm-xml osm-form osm-data
                                                 osm-road-field osm-building-field]]))

;; DATA GEN HELPERS

(defn feature-gen [n]
  (for [i (range n)]
    (hash-map :geometry {:coordinates [(rand 10) (rand 10)]
                         :type "Point"}
              :properties {"_id" i "_rank" (inc i)}
              :type "Feature")))

(defn test-geojson [n]
  {:type "FeatureCollection" :features (feature-gen n)})

;; MAP HELPERS

(defn equivalent-styles [ss ts]
  (every? #{true} (map mu/equivalent-style ss ts)))
(def map-id (domutils/new-container!))
(def m (mu/create-map map-id false))

;; TESTS

(deftest map-creation
  (testing "create map creates a leaflet map with zoom + layers control"
    (is (re-find #"leaflet-container" (dommy/attr map-id "class")))
    (is (not= nil (sel1 map-id :.leaflet-map-pane)))
    (is (not= nil (sel1 map-id :.leaflet-control-zoom)))
    (is (not= nil (sel1 map-id :.leaflet-control-layers))))
  (testing "all mapbox-tile layers are loaded"
    (is (= (map :name mu/mapbox-tiles)
           (map (comp s/trim dommy/text)
                  (-> map-id (sel1 :.leaflet-control-layers) (sel :label)))))))

(deftest loading-and-marker-actions
  (let [chan (chan)
        N 10
        geojson (test-geojson N)
        {:keys [markers feature-layer]} (mu/load-geo-json m geojson chan)
        m1 (first markers)
        fff #js {:fillColor "#fff"}
        normal-style (mu/get-ona-style :point :normal)]
    (testing "geojson loads markers into map"
      (is (= true (.hasLayer m feature-layer)))
      (is (= N (count markers))))
    (testing "marker defaults check out"
      (is (= (range N) (map mu/get-id markers)))
      (is (= (map inc (range N)) (map mu/get-rank markers)))
      (is (= (repeat N false)) (map mu/is-clicked? markers))
      (is (mu/equivalent-style normal-style
                            (first (map mu/get-style markers))))
      (is (equivalent-styles (repeat N normal-style)
                             (map mu/get-style markers))))
    (testing "clicking/unclicking reflect on is-clicked?"
      (let [_ (mu/apply-click-style m1)]
        (is (= (concat [true] (repeat (dec N) false))
               (map mu/is-clicked? markers))))
      (let [_ (mu/apply-unclick-style m1)]
        (is (= (repeat N false)) (map mu/is-clicked? markers))))
    (testing "restyle marker changes all un-clicked marker colors"
      ;; CLICK m1, and change color to #fff. Everything but m1 should change
      (let [_ (mu/apply-click-style m1)
            _ (doseq [m markers]
                (mu/re-style-marker #(hash-map :fillColor "#fff") m))]
        (is (mu/equivalent-style (mu/get-ona-style :point :clicked)
                                 (mu/get-style m1)))
        (is (mu/equivalent-style fff
                                 (mu/get-style m1 :reset)))
        (is (equivalent-styles (repeat (dec N) fff)
                               (map mu/get-style (rest markers)))))
      ;; UNCLICK m1, everything should be #fff, all reset styles also fff
      (let [_ (mu/apply-unclick-style m1)]
        (is (equivalent-styles (repeat N fff) (map mu/get-style markers)))
        (is (equivalent-styles (repeat N fff) (map #(mu/get-style % :reset) markers))))
      ;; CLEAR ALL STYLES
      (let [_ (mu/clear-all-styles markers)]
        (is (equivalent-styles (repeat N normal-style)
                               (map mu/get-style markers)))))))

;; READ fixtures into data structures using the read-file macro
;; FORMS are keywordized
(def geopoint-form (json->js->cljs
                    (read-file "test/clj/ona/fixtures/geopoint-form.json")))
(def geoshape-form (json->js->cljs
                    (read-file "test/clj/ona/fixtures/geoshape-form.json")))
(def geotrace-form (json->js->cljs
                    (read-file "test/clj/ona/fixtures/geotrace-form.json")))
;; DATA is not keywordized
(def geopoint-data (json->cljs
                    (read-file "test/clj/ona/fixtures/geopoint-data.json")))
(def geoshape-data (json->cljs
                    (read-file "test/clj/ona/fixtures/geoshape-data.json")))
(def geotrace-data (json->cljs
                    (read-file "test/clj/ona/fixtures/geotrace-data.json")))

(defn- two-num-array? [coll] (= [true true] (map number? coll)))

;; TODO: Make these tests re-run.
;; The issue at the moment is that the previous tests were in clj, and were
;; running off of fixtures loading from the filesystem. It is not *easily*
;; possible to do this via cljs
;; (see https://github.com/cemerick/clojurescript.test/pull/58)
(deftest as-geojson-tests
  (testing "as-geojson returns nil if no geofield"
    (let [no-geo-form (remove f/geofield? (f/flatten-form geopoint-form))]
      (is (nil? (mu/as-geojson no-geo-form geopoint-data)))))
  (testing "as-geojson converts point data correctly"
    (let [ptform (f/flatten-form geopoint-form)
          ptfield (first (filter #(= "geopoint" (:type %)) ptform))
          ptgeojson (mu/as-geojson geopoint-data ptform ptfield)
          geoms (map :geometry (:features ptgeojson))
          all-coords (map :coordinates geoms)]
      (is (= (map :type geoms) (repeat (count geoms) "Point")))
      (is (every? #{true} (map two-num-array? all-coords)))
      (is (= (first all-coords) [-11.59225 7.937983]))
      (testing "as-geojson drops null point data correctly"
        (is (= (count geopoint-data) (inc (count (:features ptgeojson))))))))
  (testing "as-geojson converts geoshape data correctly"
    (let [shpform (f/flatten-form geoshape-form)
          shpgeojson (mu/as-geojson geoshape-data shpform)
          geoms (map :geometry (:features shpgeojson))
          all-coords (map first (map :coordinates geoms))]
      (is (= (map :type geoms) (repeat (count geoms) "Polygon")))
      (is (every? #{true}
                  (->> all-coords (map first) (map two-num-array?))))
      ;; polygon geom = list of polygons, polygon = list of lat/lng pairs
      (is (= (-> all-coords first first) [-11.59225 7.937983]))
      (is (= (testing "as-geojson drops null point data correctly"
        (count geoshape-data) (inc (count (:features shpgeojson))))))))
  (testing "as-geojson converts geotrace data correctly"
    (let [trcform (f/flatten-form geotrace-form)
          trcgeojson (mu/as-geojson geotrace-data trcform)
          ;; linestring geom = list of lat/lng pairs
          geoms (map :geometry (:features trcgeojson))
          all-coords (map :coordinates geoms)]
      (is (= (map :type geoms) (repeat (count geoms) "LineString")))
      (is (every? #{true}
                  (->> all-coords (map first) (map two-num-array?))))
      (is (= (-> all-coords first first) [-11.59225 7.937983]))
      (testing "as-geojson drops null point data correctly"
        (is (= (count geotrace-data) (inc (count (:features trcgeojson)))))))))

(deftest geojson-from-osm-data
  "Tests that geojson can be produced from osm data.
   OSM fixtures from osm-utils-test, setup from dataview.shared."
  (shared/update-app-data! osm-data :re-rank? true)
  (shared/update-app-state-with-osm-data! osm-form osm-xml)
  (let [data-including-osm (get-in @shared/app-state [:map-page :data])
        field->geojson (partial mu/as-geojson data-including-osm osm-form)]
    (testing "geojson for osm_road field is null (there is no data for roads)"
      (is (= [] (:features (field->geojson osm-road-field)))))
    (testing "geojson for osm_building field has two polygons"
      (let [building-features (:features (field->geojson osm-building-field))]
        (is (= 2 (count building-features)))
        (is (= (repeat 2 #{:type :properties :geometry})
               (map #(-> % keys set) building-features)))
        (is (-> building-features first :geometry))
        (is (-> building-features second :geometry))
        (is (every? #{"Polygon"}
                    (map #(-> % :geometry :type) building-features)))))))
