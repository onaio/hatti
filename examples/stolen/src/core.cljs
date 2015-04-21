(ns examples.stolen.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [om.core :as om]
            [cljs.core.async :refer [<! put!]]
            [cljs-http.client :as http]
            [cljs-http.core :as http-core]
            [hatti.forms :refer [flatten-form]]
            [hatti.shared :as shared]
            [hatti.utils :refer [json->cljs url]]
            [hatti.map.components :refer [map-page]]
            [hatti.table.components :refer [table-page]]))

(enable-console-print!)

(def ona-api-base "http://ona.io/api/v1")
(defn ona-data-url [endpoint dataset-id]
  (url ona-api-base endpoint (str dataset-id ".json")))
(defn ona-formjson-url [dataset-id]
  (url ona-api-base "forms" dataset-id "form.json"))
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
(def mapbox-tiles
  [{:url "http://{s}.tile.openstreetmap.fr/hot/{z}/{x}/{y}.png"
    :name "Humanitarian OpenStreetMap Team"
    :attribution "&copy;  <a href=\"http://osm.org/copyright\">
                  OpenStreetMap Contributors.</a>
                  Tiles courtesy of
                  <a href=\"http://hot.openstreetmap.org/\">
                  Humanitarian OpenStreetMap Team</a>."}])

(go
 (let [dataset-id "33597" ;; Stolen Sculptures
       data-chan (raw-get (ona-data-url "data" dataset-id))
       form-chan (http/get (ona-formjson-url dataset-id))
       data (-> (<! data-chan) :body json->cljs)
       form (-> (<! form-chan) :body flatten-form)]
   (shared/update-app-data! data :rerank? true)
   (om/root map-page
            shared/app-state
            {:target (. js/document (getElementById "map"))
             :shared {:flat-form form
                      :map-config {:mapbox-tiles mapbox-tiles}}})
   (om/root table-page
            shared/app-state
            {:target (. js/document (getElementById "table"))
             :shared {:flat-form form}})))
