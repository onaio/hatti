(ns hatti.views.photos
  (:require [om.core :as om :include-macros true]
            [sablono.core :refer-macros [html]]
            [hatti.views :refer [photos-page]]))

(defmethod photos-page :default
  [{:keys [dataset-info]} owner]
  "Om component for the photos page."
  (om/component
   (html
    [:div.container
     [:p "This is the default photos page holder."]])))
