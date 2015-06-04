(ns hatti.routing
  (:require [om.core :as om]
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

(defn enable-dataview-routing! []
  (let [history (History.)
        navigation EventType/NAVIGATE]
    (sec/set-config! :prefix "#")
    (goog.events/listen history
                        navigation
                        #(-> % .-token sec/dispatch!))
    (doto history (.setEnabled true))))



