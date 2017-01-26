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
            [hatti.views.photos :refer [extract-images
                                        resize-image
                                        full-url-from-active-image]]
            [hatti.shared-test :refer
             [fat-form no-data small-fat-data data-gen]]
            [om.core :as om :include-macros true]))

(def photos-form (take 4 fat-form))
(def photos-column ["col1" "col2"])

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
    (is (= nil (extract-images {constants/photo {}} photos-column))))

  (let [datum {constants/photo {(keyword constants/download-url) :url}}]
    (testing "return argument with attachments if photo and download-url"
      (is (= (assoc datum :attachments [:url])
             (extract-images datum photos-column)))))

  (testing "return nil if neither attachments or photo"
    (is (= nil (extract-images {:key :value} photos-column))))

  (testing "return nil if no attachments"
    (is (= nil (extract-images {constants/_attachments []} photos-column))))

  (testing "return nil if attachments and no download-url"
    (is (= nil (extract-images {constants/_attachments [{:key :value}]}
                               photos-column))))

  (let [datum {constants/_attachments [{constants/download-url :url}]}]
    (testing "return argument if attachments and download-url"
      (is (= (assoc  datum :attachments [:url])
             (extract-images datum photos-column)))))

  (let [datum {constants/_attachments [{constants/download-url :url}
                                       {:key :value}]}]
    (testing "return only attachments with download-url"
      (is (= (assoc datum :attachments [:url])
             (extract-images datum photos-column)))))

  (let [datum {constants/_attachments [{constants/download-url :url}
                                       {constants/download-url :video-url
                                        constants/mimetype "video/mp4"}]}]
    (testing "return only attachments with download-url excluding non-images"
      (is (= (assoc datum :attachments [:url])
             (extract-images datum photos-column)))))

  (let [datum {constants/_attachments [{constants/download-url :url}]
               (first photos-column) {(keyword constants/download-url) :url1}
               (last photos-column) {(keyword constants/download-url) :url2}}]
    (testing "prefer column URL to attachments"
      (is (= (assoc datum :attachments [:url1 :url2])
             (extract-images datum photos-column))))))

(deftest full-url-from-active-image-gets-url
  (let [img (.createElement js/document "img")
        container (new-container! "active-image")
        active-image-src "https://the-url"]
    (.setAttribute img "class" "pswp__img pswp__img--placeholder")
    (.setAttribute img "src" (resize-image active-image-src 100))
    (dommy/append! (sel1 js/document "#active-image") img)
    (testing "strips last url"
      (is (= (full-url-from-active-image) active-image-src)))))
