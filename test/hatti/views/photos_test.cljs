(ns hatti.views.photos-test
  (:require-macros [cljs.test :refer (is deftest testing)]
                   [dommy.core :refer [sel sel1]])
  (:require [cljs.test :as t]
            [cljs.core.async :refer [chan]]
            [dommy.core :as dommy]
            [hatti.constants :as constants]
            [hatti.shared :as shared]
            [hatti.test-utils :refer [new-container!]]
            [hatti.views :refer [photos-page]]
            [hatti.views.photos :refer [extract-images]]
            [hatti.shared-test :refer
             [fat-form no-data small-fat-data data-gen]]
            [om.core :as om :include-macros true]))

(def photos-form (take 4 fat-form))

(defn- photo-container
  "Returns a container in which a photo component has been rendered.
   `data` arg is directly passed into the component as its cursor."
  [form]
  (let [cont (new-container!)
        arg {:shared {:flat-form form
                      :event-chan (chan)}
             :target cont}]
    (om/root photos-page shared/app-state arg)
    cont))

(deftest photos-render-properly
  (let [container (photo-container photos-form)]
    (testing "renders no photos properly"
      (is (re-find #"no photos" (-> container (sel1 :p) dommy/text))))))

(deftest extract-images-restricts-properly
  (testing "return nil if photo but no download-url"
    (is (= nil (extract-images {constants/photo {}}))))
  (let [datum {constants/photo {(keyword constants/download-url) :url}}]
    (testing "return argument if photo and download-url"
      (is (= datum (extract-images datum)))))
  (testing "return nil if neither attachments or photo"
    (is (= nil (extract-images {:key :value}))))
  (testing "return nil if no attachments"
    (is (= nil (extract-images {constants/_attachments []}))))
  (testing "return nil if attachments and no download-url"
    (is (= nil (extract-images {constants/_attachments [{:key :value}]}))))
  (let [datum {constants/_attachments [{constants/download-url :url}]}]
    (testing "return argument if attachments and download-url"
      (is (= datum (extract-images datum)))))
  (let [datum {constants/_attachments [{constants/download-url :url}
                                       {:key :value}]}]
    (testing "return only attachments with download-url"
      (is (= {constants/_attachments [{constants/download-url :url}]}
             (extract-images datum)))))
  (let [datum {constants/_attachments [{constants/download-url :url}
                                       {constants/download-url :video-url
                                        constants/mimetype "video/mp4"}]}]
    (testing "return only attachments with download-url"
      (is (= {constants/_attachments [{constants/download-url :url}]}
             (extract-images datum))))))
