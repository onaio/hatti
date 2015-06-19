(ns hatti.routing
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :refer [<! chan put! timeout]]
            [om.core :as om]
            [secretary.core :as sec :refer-macros [defroute]]
            [hatti.views.dataview :refer [activate-view!]]
            [hatti.shared :as shared]
            [goog.events :as events]
            [goog.history.EventType :as EventType])
  (:import goog.History))

(defroute "/:view" {:keys [view]}
  "Checks if this view is one of the allowed ones.
  If so, switches the selected view via app-state."
  (activate-view! view))

(defroute "/:view/:record_id" {:keys [view record_id]}
  "For view = map | table, routes to view AND selects submission_id"
  (when (contains? #{"map" "table"} view)
    (activate-view! view)
    (go
     (<! (timeout 4000))
     (.log js/console record_id)
     (put! shared/event-chan {:submission-to-rank 5}))))

(defn enable-dataview-routing! []
  (let [history (History.)
        navigation EventType/NAVIGATE]
    (sec/set-config! :prefix "#")
    (goog.events/listen history
                        navigation
                        #(-> % .-token sec/dispatch!))
    (doto history (.setEnabled true))))
