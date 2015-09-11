(ns hatti.utils.om.state
  (:require [om.core :as om :include-macros true]))

(defn- transact!
  "A transact! function that optionally uses swap! if this atom is not yet transactable?"
  [app-state]
  (if (satisfies? om/ITransact app-state) om/transact! swap!))

(defn transact-app-state!
  "Like om/transact! but also works on atoms that are not transactable?"
  ([app-state transact-fn]
   ((transact! app-state) app-state transact-fn))
  ([app-state ks transact-fn]
   ((transact! app-state) app-state #(update-in % ks transact-fn))))

(defn update-app-state!
  "Like om/update! but also works on atoms that are not transactable?"
 ([app-state update-val]
  (transact-app-state! app-state #(-> update-val)))
 ([app-state ks update-val]
  (transact-app-state! app-state ks #(-> update-val))))

(defn merge-into-app-state!
  "Merges provided state into existing app-state, possibly after zoomint into ks."
  ([app-state state-to-merge]
   (transact-app-state! app-state #(merge % state-to-merge)))
  ([app-state ks state-to-merge]
   (transact-app-state! app-state ks #(merge % state-to-merge))))
