(ns examples.stolen.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [om.core :as om]
            [cljs.core.async :refer [<! put!]]
            [cljsjs.papaparse]
            [milia.api.dataset :as api]
            [milia.api.http :refer [parse-http]]
            [milia.utils.remote :as milia-remote :refer [make-url]]
            [hatti.ona.csv-reader :refer [progressively-read-csv!]]
            [hatti.ona.forms :refer [flatten-form]]
            [hatti.ona.post-process :refer [integrate-attachments!]]
            [hatti.routing :as routing]
            [hatti.shared :as shared]
            [hatti.utils :refer [json->cljs]]
            [hatti.views :as views]
            [hatti.views.dataview]))

(enable-console-print!)

;; CONFIG

(swap! milia-remote/hosts merge {:ui "localhost:8000"
                                 :data "ona.io"
                                 :ona-api-server-protocol "https"})
(def dataset-id "33597") ;; Stolen Sculptures
;(def dataset-id "73926") ;; 4799 record dataset

(def mapbox-tiles
  [{:url "http://{s}.tile.openstreetmap.fr/hot/{z}/{x}/{y}.png"
    :name "Humanitarian OpenStreetMap Team"
    :attribution "&copy;  <a href=\"http://osm.org/copyright\">
                  OpenStreetMap Contributors.</a>
                  Tiles courtesy of
                  <a href=\"http://hot.openstreetmap.org/\">
                  Humanitarian OpenStreetMap Team</a>."}])
(swap! milia-remote/*credentials* merge {:temp-token ""})

;; HELPER
(defn chart-getter [field-name]
  (let [suffix (str dataset-id ".json?field_name=" field-name)
        chart-url (make-url "charts" suffix)]
    (parse-http :get chart-url)))

;; GET AND RENDER
(go
 (let [data-chan (api/data dataset-id
                           :raw? true
                           :format "csv"
                           :accept-header "text/*")
       form-chan (api/form dataset-id)
       info-chan (api/metadata dataset-id)
       form (-> (<! form-chan) :body flatten-form)
       info (-> (<! info-chan) :body)
       reader (fn [data completed?]
                (shared/add-to-app-data! shared/app-state data :completed? completed?))]
   (shared/transact-app-state! shared/app-state [:dataset-info] (fn [_] info))
   (integrate-attachments! shared/app-state form)
   (om/root views/tabbed-dataview
            shared/app-state
            {:target (. js/document (getElementById "map"))
             :shared {:flat-form form
                      :map-config {:mapbox-tiles mapbox-tiles}}
             :opts {:chart-get chart-getter}})
   (progressively-read-csv! (:body (<! data-chan)) reader)))
(routing/enable-dataview-routing!)
