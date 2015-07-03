(ns hatti.views.settings
   (:require-macros [cljs.core.async.macros :refer [go]])
   (:require [om.core :as om :include-macros true]
             [sablono.core :as html :refer-macros [html]]
             [hatti.views :refer [form-details settings-page]]))

(defmethod form-details :default
  [form owner]
  "Form section of the Settings page, very basic."
  (om/component
   (let [{:keys [title description downloadable id_string editing?]} form]
     (html
      [:form {:id "settings-form" :class "pure-form pure-form-aligned" }
       [:fieldset
        [:div.pure-control-group
         [:label "Name"]
         [:span {:class "detail-form-name"} title]]
        [:div.pure-control-group
         [:label "Description"]
         [:span {:class "detail-form-description"} description]]
        [:div.pure-control-group
         [:label "Form ID"]
         [:span {:class "detail-form-id"} id_string]]
        [:div.pure-control-group
         [:label {:for "form-status"} "Status"]
         [:span {:class "detail-form-active"}
          (if downloadable "Active" "Inactive")]]]]))))

(defmethod settings-page :default
  [{:keys [dataset-info]} owner]
  "Om component for the whole Settings page."
  (om/component
   (html
    [:div.container
     (om/build form-details dataset-info)])))
