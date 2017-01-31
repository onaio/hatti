(ns hatti.ona.post-process-test
  (:require-macros [cljs.test :refer (is deftest testing)]
                   [dommy.core :refer [sel1 sel]]
                   [hatti.macros :refer [read-file]])
  (:require [cljs.core.async :refer [chan]]
            [hatti.ona.post-process :as post-process]))

;; TEST DATA
(def osm-xml (read-file "test/fixtures/osm.xml"))

(def osm-data
  [{"osm_building" "OSMWay234134804.osm"
    "_id" "1234"
    "_rank" 1
    "_submission_time" "2015-02-18T14:17:10"
    "form_completed" "2015-02-18T17:15:40.961+03"
    "_submitted_by" "ukdemo"
    "_xform_id_string" "osm_example"
    "meta/instanceID" "uuid:e12d0a41-bb5d-44b9-be84-ea3f7039acce"
    "formhub/uuid" "4b240b72833143dab9c66efaf669e3e9"}
   {"osm_building" "OSMWay234136126.osm"
    "_id" "1235"
    "_rank" 2
    "_submission_time" "2015-02-18T14:16:03"
    "form_completed" "2015-02-18T17:14:38.906+03"
    "_submitted_by" "ukdemo"
    "_xform_id_string" "osm_example"
    "meta/instanceID" "uuid:a6456416-ca1d-462e-9e0a-dbce0aea20f9"
    "formhub/uuid" "4b240b72833143dab9c66efaf669e3e9"}
   {"osm_road" "OSMWay1111.osm"
    "osm_building" "OSMWay2222.osm"
    "_id" "1236"
    "_rank" 3
    "_submission_time" "2015-02-19T17:14:38.906+03"}])

(def osm-road-field {:type "osm" :name "osm_road" :full-name "osm_road"})

(def osm-building-field
  {:type "osm" :name "osm_building" :full-name "osm_building"})

(def osm-form [osm-road-field osm-building-field])

;; TESTS
(def osm-ona-link (post-process/ona-osm-link osm-data osm-form))
(def osmgeo (post-process/osm-xml->geojson osm-xml))
(def id->tags (post-process/osm-id->osm-data osm-data osm-form osm-xml))

(deftest osm-ona-link-data-structure-is-calculated-properly
  (testing "keys are all the osm-ids present in data"
    (is (= (sort (keys osm-ona-link))
           (list "1111" "2222" "234134804" "234136126"))))
  (testing "_id _rank field are pulled into the link data structure correctly"
    (is (= osm-building-field
           (:field (osm-ona-link (rand-nth ["2222" "234134804" "234136126"])))))
    (is (= (osm-ona-link "1111")
           {"_id" "1236" "_rank" 3 :field osm-road-field}))
    (is (= (osm-ona-link "2222")
           {"_id" "1236" "_rank" 3 :field osm-building-field}))
    (is (every? #(= #{:id "_rank" :field})
                (->> osm-ona-link vals (map keys) (map set))))))

(deftest osm-to-geojson-conversion
  (testing "osm->geojson converts osm xml string to geojson"
    (is (= #{:type :features} (-> osmgeo keys set)))
    (is (= "FeatureCollection" (osmgeo :type)))))

(deftest dataview-data-conversion
  (testing "osmid->tags produces osm tags from osm id"
    (let [tags1 (-> "234134804" id->tags  :tags)
          tags2 (-> "234136126" id->tags  :tags)]
      (is (= "234136126" (-> "234136126" id->tags :osm-id)))
      (is (= "way" (-> "234136126" id->tags :type)))
      (is (= "greta" (-> "234136126" id->tags :name)))
      (is (= "234134804" (-> "234134804" id->tags :osm-id)))
      (is (= "way" (-> "234134804" id->tags :type)))
      (is (= "" (-> "234134804" id->tags :name)))
      (is (= (-> tags1 keys set)
             #{:building :addr:postcode :addr:street :name :building:levels
               :amenity :addr:housenumber :name:fr :addr:city}))
      (is (= (-> tags2 keys set)
             #{:building :addr:postcode :addr:street :name :building:levels
               :amenity :addr:housenumber :name:fr :addr:city})))))

(deftest test-filter-media
  (let [flat-form '({:type "video"}
                    {:type "nothing"}
                    {:type "image"}
                    {:type "photo"}
                    {:type "blah"})
        flat-form-video '({:type "video"}
                          {:type "blah"})
        flat-form-images '({:type "nothing"}
                           {:type "image"}
                           {:type "photo"}
                           {:type "blah"})]
    (testing "filters for images and videos"
      (is (= (post-process/filter-media flat-form)
             '({:type "video"} {:type "image"} {:type "photo"}))))
    (testing "filters for video"
      (is (= (post-process/filter-media flat-form-video)
             '({:type "video"}))))
    (testing "filters for images"
      (is (= (post-process/filter-media flat-form-images)
             '({:type "image"} {:type "photo"}))))))

(deftest get-matching-name
  (testing "get-matching-name finds matching filename using attachement name
  from list of attachments"
    (is (= (post-process/get-matching-name
            "1478203839187.jpg"
            ["1474634658923_wiuyrXR.jpg"
             "1478203839187_wijUzUf.jpg"
             "1478404659734.jpg"])
           "1478203839187_wijUzUf.jpg"))
    (is (= (post-process/get-matching-name
            "السيره الذاتيه )  محمد خلف )-22_6_4.doc"
            ["السيره_الذاتيه___محمد_خلف_-22_6_4.doc"])
           "السيره الذاتيه )  محمد خلف )-22_6_4.doc"))))
