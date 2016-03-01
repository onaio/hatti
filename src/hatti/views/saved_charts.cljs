(ns hatti.views.saved-charts
  (:require [hatti.views :refer [saved-charts-page]]
            [om.core :as om :include-macros]
            [sablono.core :as html :refer-macros [html]]))

(defmethod saved-charts-page :default
  [_ _]
  (reify
    om/IRender
    (render [_]
      (html [:div.container "Placeholder"]))))
