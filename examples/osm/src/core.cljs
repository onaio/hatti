(ns examples.osm.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [om.core :as om]
            [cljs.core.async :refer [<! put!]]
            [cljs-http.client :as http]
            [cljs-http.core :as http-core]
            [hatti.ona.forms :refer [flatten-form]]
            [hatti.ona.post-process :refer [integrate-osm-data!]]
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
(def mapbox-tiles
  [{:url "http://{s}.tile.openstreetmap.fr/hot/{z}/{x}/{y}.png"
    :name "Humanitarian OpenStreetMap Team"
    :attribution "&copy;  <a href=\"http://osm.org/copyright\">
                  OpenStreetMap Contributors.</a>
                  Tiles courtesy of
                  <a href=\"http://hot.openstreetmap.org/\">
                  Humanitarian OpenStreetMap Team</a>."}])

(go
 (let [data-chan (raw-get "data/31066_data.json")
       osm-chan (raw-get "data/31066_data.osm")
       form-chan (http/get "data/31066_form.json")
       data (-> (<! data-chan) :body json->cljs)
       form (-> (<! form-chan) :body flatten-form)
       osm-xml (-> (<! osm-chan) :body)]
   (shared/update-app-data! shared/app-state data :rerank? true)
   (integrate-osm-data! shared/app-state form osm-xml)
   (om/root views/map-page
            shared/app-state
            {:target (. js/document (getElementById "map"))
             :shared {:flat-form form
                      :map-config {:mapbox-tiles mapbox-tiles}}})))
