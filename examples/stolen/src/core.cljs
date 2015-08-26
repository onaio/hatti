(ns examples.stolen.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [om.core :as om]
            [cljs.core.async :refer [<! put! chan timeout]]
            [cljsjs.papaparse]
            [milia.api.dataset :as api]
            [milia.api.http :refer [parse-http]]
            [milia.utils.remote :as milia-remote :refer [make-url]]
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
;(def dataset-id "33597") ;; Stolen Sculptures
(def dataset-id "73926") ;; 4799 record dataset

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

(defn push-to-app-state!
  "Takes data in the vector inside *agg*, adds it to app state,
   and clears *agg*."
  [*agg* & {:keys [completed?]}]
  (shared/add-to-app-data! shared/app-state @*agg* :completed? completed?)
  (reset! *agg* []))

(defn read-next-chunk!
  "If *n* is a power of 2 above 100, then flush the data into app-state,
  else store it inside *agg*, increment *n*, and move on."
  [data-chunk parser *n* *agg*]
  (go
   (swap! *n* inc)
   (swap! *agg* conj (first (js->clj (.-data data-chunk))))
   (when (and (> @*n* 1000) (integer? (.sqrt js/Math @*n*)))
     (push-to-app-state! *agg*)
     (.pause parser)
     (<! (timeout 2))
     (.resume parser))))

(defn chunk-reader []
  "Returns a callback for step/chunk for papa-parse, ie,
   a function that can be called on chunk and parser.
   Closes two atoms: an int incrementor inside *n* and
                     a vector aggregator inside *agg*."
  (let [*n* (atom 0)
        *agg* (atom [])]
    {:step (fn [chnk parser] (read-next-chunk! chnk parser *n* *agg*))
     :complete #(push-to-app-state! *agg* :completed? true)}))

;; GET AND RENDER
(go
 (let [parse (fn [s & [config]] (.parse js/Papa s config))
       data-chan (api/data dataset-id
                           :raw? true
                           :format "csv"
                           :accept-header "text/*")
       form-chan (api/form dataset-id)
       info-chan (api/metadata dataset-id)
       {:keys [step complete]} (chunk-reader)
       form (-> (<! form-chan) :body flatten-form)
       info (-> (<! info-chan) :body)]
   (shared/transact-app-state! shared/app-state [:dataset-info] (fn [_] info))
   (integrate-attachments! shared/app-state form)
   (om/root views/tabbed-dataview
            shared/app-state
            {:target (. js/document (getElementById "map"))
             :shared {:flat-form form
                      :map-config {:mapbox-tiles mapbox-tiles}}
             :opts {:chart-get chart-getter}})
   (-> (<! data-chan) :body
       (parse (clj->js {:header true
                        :dynamicTyping true
                        :skipEmptyLines true
                        :step step
                        :complete complete})))))
(routing/enable-dataview-routing!)
