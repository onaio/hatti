(ns hatti.ona.csv-reader
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :refer [<! put! chan timeout close!]]))

(defn process-aggregated-data!
  "Calls callback on aggregated data that come via messages in channel.
   Callback accepts two arguments: data (cljs list), and completed? (bool)
  If there is a parser in message, resume it after a timeout."
  [agg-data-channel callback]
  (go
   (while true
     (let [{:keys [data completed? parser]} (<! agg-data-channel)]
       (when data (callback data completed?))
       ;; Timeout so browser can do some rendering before parsing again.
       (<! (timeout 100))
       (when parser (.resume parser))
       (when completed? (close! agg-data-channel))))))

(defn read-next-chunk!
  "On each step of data, increment a counter *n*, and put data in a list *agg*.
   After a exponentially increasing # of steps have been processes,
   the aggregated data gets flushed into a channel, and parser is paused.
   The channel reader should resume the parser after processing the data."
  [data-chunk parser *n* *agg* channel]
   (swap! *n* inc)
   (swap! *agg* conj (first (js->clj (.-data data-chunk))))
   (when (and (>= @*n* 100) (integer? (.log10 js/Math @*n*)))
     (.pause parser)
     (put! channel {:data @*agg* :parser parser})
     (reset! *agg* [])))

(defn progressively-read-csv!
  "Given csv-string + callback for incremental data,
   progressively parses csv (with exponential chunk processing).
   Channel-based asynchronization + waits allow browser to render in bckgrnd."
  [csv-string chunk-callback]
  (let [*n* (atom 0)
        *agg* (atom [])
        channel (chan)
        step (fn [chnk parser]
               (read-next-chunk! chnk parser *n* *agg* channel))
        complete #(put! channel {:data @*agg* :completed? true})
        config (clj->js {:header true
                         :skipEmptyLines true
                         :step step
                         :complete complete})]
    (process-aggregated-data! channel chunk-callback)
    (.parse js/Papa csv-string config)))
