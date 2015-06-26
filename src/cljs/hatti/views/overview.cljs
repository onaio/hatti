(ns hatti.views.overview
  (:require [om.core :as om :include-macros true]
            [sablono.core :refer-macros [html]]
            [hatti.views :refer [overview-page]]))


(defmethod overview-page :default
  [{:keys [dataset-info]} owner]
  "Om component for the overview page."
  (om/component
    (html
      [:div.container
       [:p "This is the default overview page holder."]])))
