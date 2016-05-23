(ns hatti.utils.om.state
  (:require [chimera.om.state :refer [transact!]]))

(defn update-app-state!
  "Like om/update! but also works on atoms that are not transactable?"
  ([app-state update-val]
   (transact! app-state #(-> update-val)))
  ([app-state ks update-val]
   (transact! app-state ks #(-> update-val))))
