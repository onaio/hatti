(ns hatti.views.map-table
  (:require [om.core :as om :include-macros true]
            [sablono.core :refer-macros [html]]
            [hatti.views :refer [map-table-page]]))

(defmethod map-table-page :default
  [{:keys [dataset-info]} owner]
  "Om component for the Map Table Page."
  (om/component
   (html
    [:div.container
     [:p "This is the default map table page."]])))
