(ns hatti.views.user-guide
  (:require [hatti.views :refer [user-guide-page]]
            [om.core :as om :include-macros true]
            [sablono.core :as html :refer-macros [html]]))

(defmethod user-guide-page :default
  [cursor owner]
  (reify
    om/IRender
    (render [_]
      (html [:p "Guide me"]))))
