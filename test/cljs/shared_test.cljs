(ns ona.dataview.shared-test
  (:require-macros [cljs.test :refer (is deftest testing)]
                    [dommy.macros :refer [node sel sel1]])
  (:require [cljs.test :as t]
            [dommy.core :as dommy]
            [om.core :as om :include-macros true]
            [ona.api.io :refer [make-url]]
            [ona.utils.dom :as domutils]
            [ona.utils.interop :refer [format]]
            [ona.dataview.shared :as shared]
            [ona.dataview.osm-utils-test :refer [osm-xml osm-data osm-form]]))

;; SAMPLE DATA

(defn form-gen [n]
  (for [i (range n)]
    {:type (rand-nth ["string" "select one"])
     :full-name (str "hello" i)
     :name (str "hello" i)
     :label (str "Hello: " i)}))

(defn data-gen [ncol nrow]
  (let [rf (fn [max] (format "%02d" (inc (rand-int max))))]
  (for [i (range nrow)]
    (apply merge {"_id" i
                  "_rank" (inc i)
                  "_submission_time" (str "2012-" (rf 12) "-" (rf 30))}
                 (for [j (range ncol)] {(str "hello" j) (str "goodbye" j)})))))

(def thin-form (form-gen 1))
(def fat-form (form-gen 100))

(def no-data (data-gen 0 0))
(def small-thin-data (data-gen 1 10))
(def big-thin-data   (data-gen 1 100))
(def small-fat-data  (data-gen 100 10))
(def big-fat-data    (data-gen 100 100))

;; TESTS

(deftest language-selector-tests
  (let [c (domutils/new-container!)
        langs [:Nepali :English :French :Swahili]
        current :Nepali]
    (swap! shared/app-state assoc-in [:languages] {:all langs :current current})
    (om/root shared/language-selector nil {:target c})
    (testing "language-selector renders all languages"
      (is (= (->> (sel c :li) (map dommy/text)) (map name langs)))
      (is (= (dommy/text (sel1 c :span.t-underline)) (name current))))
    (testing "language-selector renders English as EN"
      (swap! shared/app-state assoc-in [:languages :current] :English)
      (om/root shared/language-selector nil {:target c})
      (is (= (dommy/text (sel1 c :span.t-underline)) "EN")))))

(deftest update-data-on!-works
  (shared/update-app-data! small-thin-data :rerank? true)
  (testing "update-data-on! :delete works"
    (let [initial-data @shared/app-state
          ids (fn [data] (map #(get % "_id") (get-in data [:map-page :data])))]
      (shared/update-data-on! :delete {:instance-id 1})
      (is (contains? (-> initial-data ids set) 1))
      (is (not (contains? (-> @shared/app-state ids set) 1))))))

(deftest data-is-extracted-from-osm-properly
  (shared/update-app-data! osm-data :re-rank? true)
  (shared/update-app-state-with-osm-data! osm-form osm-xml)
  (testing "updating data with osm bits works"
    (let [data (get-in @shared/app-state [:map-page :data])]
      (is (nil? (-> data (nth 2) (get "osm_building"))))
      (is (= (-> data first (get "osm_building") keys)
             (list :osm-id :type :geom :name :tags)))
      (is (= "way" (-> data first (get "osm_building") :type)))
      (is (= "Polygon" (-> data first (get "osm_building") :geom :type))))))

(deftest integrate-attachments
  (let [data [{"_attachments" [{"filename" "prabhasp/attachments/Bhkt36_hist.jpg"
                                "id" 287633}
                               {"filename" "prabhasp/attachments/Bhkt36_hist2.jpg"
                                "id" 287632 }]
               "historic_photo" "Bhkt36_hist2.jpg"
               "historic_photo2" "Bhkt36_hist.jpg"}]
        form [{:type "photo" :name "historic_photo" :full-name "historic_photo"}
              {:type "photo" :name "historic_photo2" :full-name "historic_photo2"}]]
    (testing "attachments are integrated properly"
      (let [integrated-data (shared/integrate-attachments form data)
            revised-record (first integrated-data)]
        (is (= (-> revised-record (get "historic_photo") :download_url)
               (make-url "files/287632?filename=prabhasp/attachments/Bhkt36_hist2.jpg")))
        (is (= (-> revised-record (get "historic_photo") :small_download_url)
               (make-url "files/287632?filename=prabhasp/attachments/Bhkt36_hist2.jpg&suffix=small")))))
    (testing "data with no images doesn't get touched"
      (let [form [{:type "string" :name "historic_photo" :full-name "historic_photo"}
                  {:type "string" :name "historic_photo2" :full-name "historic_photo2"}]
            untouched-data (shared/integrate-attachments form data)]
        (is (= data untouched-data))))))

(deftest integrate-attachments-with-repeats
  (let [data [{"repeat" [{"repeat/photo1" "Bhkt36_hist2.jpg"
                          "repeat/photo2" "Bhkt36_hist.jpg"}]
               "_attachments"
               [{"filename" "prabhasp/attachments/Bhkt36_hist.jpg"
                 "id" 287633}
                {"filename" "prabhasp/attachments/Bhkt36_hist2.jpg"
                 "id" 287632 }]}]
        form [{:type "repeat" :name "repeat" :full-name "repeat"
               :children [{:type "photo" :name "photo1" :full-name "repeat/photo1"}
                          {:type "photo" :name "photo2" :full-name "repeat/photo2"}]}]]
    (testing "data is unchanged if no repeats"
      (let [new-form (assoc-in form [0 :type] "group")]
        (is (= (shared/integrate-attachments-in-repeats new-form data)
               data))))
    (testing "attachments are integrated properly"
      (let [integrated-data (shared/integrate-attachments-in-repeats form data)
            revised-record (first integrated-data)]
        (is (= (-> revised-record (get "repeat") first (get "repeat/photo1") :download_url)
               (make-url "files/287632?filename=prabhasp/attachments/Bhkt36_hist2.jpg")))
        (is (= (-> revised-record (get "repeat") first (get "repeat/photo2") :download_url)
               (make-url "files/287633?filename=prabhasp/attachments/Bhkt36_hist.jpg")))))))
