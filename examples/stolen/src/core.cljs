(ns examples.stolen.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [om.core :as om]
            [cljs.core.async :refer [<! put!]]
            [cljs-http.client :as http]
            [cljs-http.core :as http-core]
            [hatti.ona.forms :refer [flatten-form]]
            [hatti.ona.post-process :refer [integrate-attachments!]]
            [hatti.ona.urls :as ona-urls]
            [hatti.shared :as shared]
            [hatti.utils :refer [json->cljs url]]
            [hatti.views :as views]))

(enable-console-print!)

(def raw-request
  "An almost 'batteries-included' request, similar to cljs-http.client/request.
   Contains everything except response decoding."
  (-> http-core/request
      http/wrap-accept
      http/wrap-form-params
      http/wrap-content-type
      http/wrap-json-params
      http/wrap-edn-params
      http/wrap-query-params
      http/wrap-basic-auth
      http/wrap-oauth
      http/wrap-android-cors-bugfix
      http/wrap-method
      http/wrap-url))
(defn raw-get
  [url & [req]]
  "Returns raw get output given a url, without decoding json/edn/transit output."
  (raw-request (merge req {:method :get :url url})))

;; CONFIG
(def dataset-id "33597") ;; Stolen Sculptures
(def mapbox-tiles
  [{:url "http://{s}.tile.openstreetmap.fr/hot/{z}/{x}/{y}.png"
    :name "Humanitarian OpenStreetMap Team"
    :attribution "&copy;  <a href=\"http://osm.org/copyright\">
                  OpenStreetMap Contributors.</a>
                  Tiles courtesy of
                  <a href=\"http://hot.openstreetmap.org/\">
                  Humanitarian OpenStreetMap Team</a>."}])

(defn chart-getter [field-name]
  (http/get (ona-urls/chart-url dataset-id field-name)))

(go
 (let [data-chan (raw-get (ona-urls/data-url "data" dataset-id))
       form-chan (http/get (ona-urls/formjson-url dataset-id))
       info-chan (http/get (ona-urls/data-url "forms" dataset-id))
       data (-> (<! data-chan) :body json->cljs)
       form (-> (<! form-chan) :body flatten-form)
       info (-> (<! info-chan) :body)]
   (shared/update-app-data! shared/app-state data :rerank? true)
   (shared/transact-app-state! shared/app-state [:dataset-info] (fn [_] info))
   (integrate-attachments! shared/app-state form)
   (om/root views/tabbed-dataview
            shared/app-state
            {:target (. js/document (getElementById "map"))
             :shared {:flat-form form
                      :map-config {:mapbox-tiles mapbox-tiles}}
             :opts {:chart-get chart-getter}})))
