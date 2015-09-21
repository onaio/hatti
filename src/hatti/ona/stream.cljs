(ns hatti.ona.stream
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :refer [<! put! chan timeout close!]]
            [cljsjs.oboe]))

(def small-delay 10)

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
       (<! (timeout small-delay))
       (when completed? (close! agg-data-channel))))))

(defn read-next-chunk!
  "On each step of data, increment read-count atom, and put data in a list agg.
   After a exponentially increasing # of steps have been processed,
   the aggregated data gets flushed into a channel, and parser is paused.
   The channel reader should resume the parser after processing the data."
  [data-chunk read-count agg channel]
  (swap! read-count inc)
  (swap! agg conj data-chunk)
  (when (and (>= @read-count 100) (integer? (.log10 js/Math @read-count)))
    (put! channel {:data @agg})
    (reset! agg [])
    (.drop js/oboe)))

(defn streamingly-read-json!
  "Given url, a node pattern matcher, and a callback, streaming-read json."
  [url stepfn & {:keys [oboe-headers]}]
  (let [read-count (atom 0)
        agg (atom [])
        channel (chan)
        return-channel (chan)
        oboe-params (clj->js {:url url
                              :headers oboe-headers
                              :withCredentials true})
        step (fn [line]
               (read-next-chunk! (js->clj line) read-count agg channel))
        done (fn []
               (put! channel {:data @agg :completed? true})
               (close! return-channel))]
    (process-aggregated-data! channel stepfn)
    (-> (.oboe js/window oboe-params)
        (.node "!.*" step)
        (.done done))
    return-channel))
