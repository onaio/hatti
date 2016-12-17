(ns hatti.views.report-view
  (:require [om.core :as om :include-macros true]
            [sablono.core :refer-macros [html]]
            [hatti.views :refer [report-view-page]]))

(defmethod report-view-page :default
  [{:keys [dataset-info]} owner]
  "Om component for the Report View Page."
  (om/component
   (html
    [:div.container
     [:p "This is the default report view page."]])))
