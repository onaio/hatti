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

(defn put-aggregated-data-into-state!
  "Push the aggregated data that comes as messages into provided channel
   into app-state. If there is a parser passed in, resume it."
  [agg-data-channel]
  (go
   (while true
     (let [{:keys [data completed? parser]} (<! agg-data-channel)]
       (shared/add-to-app-data! shared/app-state data :completed? completed?)
       ;; give browser some time to render things starting the parser again
       (<! (timeout 100))
       (when parser (.resume parser))))))

(defn read-next-chunk!
  "If *n* is a power of 2 above 100, then flush the data into app-state,
  else store it inside *agg*, increment *n*, and move on."
  [data-chunk parser *n* *agg* channel]
   (swap! *n* inc)
   (swap! *agg* conj (first (js->clj (.-data data-chunk))))
   (when (and (>= @*n* 100) (integer? (.log10 js/Math @*n*)))
     (put! channel {:data @*agg* :parser parser})
     (.pause parser)
     (reset! *agg* [])))

(defn chunk-reader []
  "Returns a callback closure for step/chunk for papa-parse, ie, a function
   that can be called on chunk and parser.
   Closure contains an incrementor atom (*n*), a data aggregator atom (*agg*),
   and a channel that the contents of *agg* will be flushed into app-state."
  (let [*n* (atom 0)
        *agg* (atom [])
        channel (chan)]
    (put-aggregated-data-into-state! channel)
    {:step (fn [chnk parser]
             (read-next-chunk! chnk parser *n* *agg* channel))
     :complete #(put! channel {:data @*agg* :completed? true})}))

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
                        :skipEmptyLines true
                        :step step
                        :complete complete})))))
(routing/enable-dataview-routing!)
