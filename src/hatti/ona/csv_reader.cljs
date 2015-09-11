(ns hatti.ona.csv-reader
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :refer [<! put! chan timeout close!]]
            [cljsjs.oboe]))

(defn process-aggregated-data!
  "Calls callback on aggregated data that come via messages in channel.
   Callback accepts two arguments: data (cljs list), and completed? (bool)
  If there is a parser in message, resume it after a timeout."
  [agg-data-channel callback]
  (go
   (while true
     (let [{:keys [data completed? ]} (<! agg-data-channel)]
       (when data (callback data completed?))
       ;; Timeout so browser can do some rendering before parsing again.
       (<! (timeout 10))
       (when completed? (close! agg-data-channel))))))

(defn read-next-chunk!
  "On each step of data, increment a counter *n*, and put data in a list *agg*.
   After a exponentially increasing # of steps have been processes,
   the aggregated data gets flushed into a channel, and parser is paused.
   The channel reader should resume the parser after processing the data."
  [data-chunk *n* *agg* channel]
  (swap! *n* inc)
  (swap! *agg* conj data-chunk)
  (when (and (>= @*n* 100) (integer? (.log10 js/Math @*n*)))
    (put! channel {:data @*agg*})
    (reset! *agg* [])))

(defn streamingly-read-json!
  "Given url, a node pattern matcher, and a callback, streaming-read json."
  ([url stepfn]
   "streamingly read array json. !.* = everything immediately inside root."
   (streamingly-read-json! url "!.*" stepfn))
  ([url node-pattern stepfn]
   (let [*n* (atom 0)
         *agg* (atom [])
         channel (chan)
         return-channel (chan)
         oboe (.oboe js/window url)
         step (fn [line]
                (read-next-chunk! (js->clj line) *n* *agg* channel))
         done (fn []
                (.log js/console "DONE!")
                (put! channel {:data @*agg* :completed? true})
                (close! return-channel)) ]
     (process-aggregated-data! channel stepfn)
     (-> (.oboe js/window url)
         (.node node-pattern step)
         (.done done))
     return-channel)))
