(ns ona.dataview.osm-utils-test
  (:require-macros [cljs.test :refer (is deftest testing)]
                   [dommy.macros :refer [sel1 sel]])
  (:require [cljs.test :as t]
            [cljs.core.async :refer [chan]]
            [clojure.string :as s]
            [dommy.core :as dommy]
            [ona.utils.dom :as domutils]
            [ona.dataview.osm-utils :as osm]))

;; TEST DATA
(def osm-xml
  "<osm version=\"0.6\" generator=\"OpenMapKit 0.1\" user=\"theoutpost\"><node id=\"2424321042\" version=\"1\" changeset=\"17413412\" timestamp=\"2013-08-19T16:00:40Z\" lat=\"23.7084503\" lon=\"90.4099384\"/><node id=\"2424321024\" version=\"1\" changeset=\"17413412\" timestamp=\"2013-08-19T16:00:40Z\" lat=\"23.7084082\" lon=\"90.4099386\"/><node id=\"2424321036\" version=\"1\" changeset=\"17413412\" timestamp=\"2013-08-19T16:00:40Z\" lat=\"23.7084393\" lon=\"90.4096416\"/><node id=\"2424321045\" version=\"1\" changeset=\"17413412\" timestamp=\"2013-08-19T16:00:40Z\" lat=\"23.7084565\" lon=\"90.4094703\"/><node id=\"2424320998\" version=\"1\" changeset=\"17413412\" timestamp=\"2013-08-19T16:00:39Z\" lat=\"23.7083473\" lon=\"90.4094587\"/><node id=\"2424320975\" version=\"1\" changeset=\"17413412\" timestamp=\"2013-08-19T16:00:39Z\" lat=\"23.7082974\" lon=\"90.4100264\"/><node id=\"2424321026\" version=\"1\" changeset=\"17413412\" timestamp=\"2013-08-19T16:00:40Z\" lat=\"23.708412\" lon=\"90.4100376\"/><node id=\"2424321022\" version=\"1\" changeset=\"17413412\" timestamp=\"2013-08-19T16:00:39Z\" lat=\"23.7084075\" lon=\"90.4100896\"/><node id=\"2424321032\" version=\"1\" changeset=\"17413412\" timestamp=\"2013-08-19T16:00:40Z\" lat=\"23.7084307\" lon=\"90.4100929\"/><node id=\"2424321031\" version=\"1\" changeset=\"17413412\" timestamp=\"2013-08-19T16:00:40Z\" lat=\"23.7084287\" lon=\"90.410115\"/><node id=\"2424321063\" version=\"1\" changeset=\"17413412\" timestamp=\"2013-08-19T16:00:41Z\" lat=\"23.7085065\" lon=\"90.4101088\"/><node id=\"2424321067\" version=\"1\" changeset=\"17413412\" timestamp=\"2013-08-19T16:00:41Z\" lat=\"23.7085207\" lon=\"90.4099469\"/><node id=\"2424321042\" version=\"1\" changeset=\"17413412\" timestamp=\"2013-08-19T16:00:40Z\" lat=\"23.7084503\" lon=\"90.4099384\"/><way id=\"234134804\" action=\"modify\" version=\"2\" changeset=\"17413693\" timestamp=\"2013-08-19T16:27:20Z\"><nd ref=\"2424321042\"/><nd ref=\"2424321024\"/><nd ref=\"2424321036\"/><nd ref=\"2424321045\"/><nd ref=\"2424320998\"/><nd ref=\"2424320975\"/><nd ref=\"2424321026\"/><nd ref=\"2424321022\"/><nd ref=\"2424321032\"/><nd ref=\"2424321031\"/><nd ref=\"2424321063\"/><nd ref=\"2424321067\"/><nd ref=\"2424321042\"/><tag k=\"building\" v=\"yes\"/><tag k=\"building:levels\" v=\"3\"/><tag k=\"addr:street\" v=\"\"/><tag k=\"addr:housenumber\" v=\"2\"/><tag k=\"addr:city\" v=\"\"/><tag k=\"amenity\" v=\"\"/><tag k=\"name\" v=\"\"/><tag k=\"name:fr\" v=\"\"/><tag k=\"addr:postcode\" v=\"\"/></way><node id=\"2424321144\" version=\"1\" changeset=\"17413412\" timestamp=\"2013-08-19T16:00:42Z\" lat=\"23.7087165\" lon=\"90.4107021\"/><node id=\"2424321127\" version=\"1\" changeset=\"17413412\" timestamp=\"2013-08-19T16:00:42Z\" lat=\"23.7086663\" lon=\"90.4106941\"/><node id=\"2424321130\" version=\"1\" changeset=\"17413412\" timestamp=\"2013-08-19T16:00:42Z\" lat=\"23.7086745\" lon=\"90.4106216\"/><node id=\"2424321004\" version=\"1\" changeset=\"17413412\" timestamp=\"2013-08-19T16:00:39Z\" lat=\"23.7083623\" lon=\"90.4105801\"/><node id=\"2424320993\" version=\"1\" changeset=\"17413412\" timestamp=\"2013-08-19T16:00:39Z\" lat=\"23.708337\" lon=\"90.4108062\"/><node id=\"2424321120\" version=\"1\" changeset=\"17413412\" timestamp=\"2013-08-19T16:00:42Z\" lat=\"23.7086506\" lon=\"90.4108475\"/><node id=\"2424321123\" version=\"1\" changeset=\"17413412\" timestamp=\"2013-08-19T16:00:42Z\" lat=\"23.7086586\" lon=\"90.4107839\"/><node id=\"2424321141\" version=\"1\" changeset=\"17413412\" timestamp=\"2013-08-19T16:00:42Z\" lat=\"23.7087065\" lon=\"90.4107889\"/><node id=\"2424321144\" version=\"1\" changeset=\"17413412\" timestamp=\"2013-08-19T16:00:42Z\" lat=\"23.7087165\" lon=\"90.4107021\"/><way id=\"234136126\" action=\"modify\" version=\"2\" changeset=\"17413693\" timestamp=\"2013-08-19T16:31:19Z\"><nd ref=\"2424321144\"/><nd ref=\"2424321127\"/><nd ref=\"2424321130\"/><nd ref=\"2424321004\"/><nd ref=\"2424320993\"/><nd ref=\"2424321120\"/><nd ref=\"2424321123\"/><nd ref=\"2424321141\"/><nd ref=\"2424321144\"/><tag k=\"building\" v=\"yes\"/><tag k=\"building:levels\" v=\"3\"/><tag k=\"addr:street\" v=\"\"/><tag k=\"addr:housenumber\" v=\"\"/><tag k=\"addr:city\" v=\"\"/><tag k=\"amenity\" v=\"\"/><tag k=\"name\" v=\"greta\"/><tag k=\"name:fr\" v=\"\"/><tag k=\"addr:postcode\" v=\"\"/></way></osm>")
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
(def osm-building-field {:type "osm" :name "osm_building" :full-name "osm_building"})
(def osm-form
  [osm-road-field osm-building-field])

;; TESTS
(def osm-ona-link (osm/ona-osm-link osm-data osm-form))
(def osmgeo (osm/osm-xml->geojson osm-xml))
(def id->tags (osm/osm-id->osm-data osm-data osm-form osm-xml))

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

