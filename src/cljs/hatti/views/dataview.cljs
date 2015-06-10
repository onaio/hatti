(ns hatti.views.dataview
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :refer [put!]]
             [om.core :as om :include-macros true]
             [sablono.core :as html :refer-macros [html]]
             [hatti.ona.forms :as f]
             [hatti.shared :as shared]
             [hatti.views :refer [tabbed-dataview
                                  dataview-infobar dataview-actions
                                  map-page table-page chart-page details-page]]
             [hatti.views.map]
             [hatti.views.table]
             [hatti.views.chart]
             [hatti.views.details]
             [hatti.utils :refer [click-fn pluralize-number]]))

(def dataview-map
  {:map {:view "map"
         :label "Map"
         :component map-page}
   :table {:view "table"
           :label "Table"
           :component table-page}
   :chart {:view "chart"
           :label "Summary Charts"
           :component chart-page}
   :details {:view "details"
             :label "Details"
             :component details-page}})

(defmethod dataview-actions :default
  [cursor owner]
  (om/component (html nil)))

(defmethod dataview-infobar :default
  [{:keys [num_of_submissions]} owner]
  (reify
    om/IWillMount
    (will-mount [_]
      (let [form (om/get-shared owner :flat-form)
            langs (f/get-languages form)
            default-lang (f/default-lang langs)]
        (when (f/multilingual? form)
          (shared/transact-app-state!
           shared/app-state
           [:languages]
           (fn [_] {:all langs
                    :default default-lang
                    :current default-lang})))))
    om/IRender
    (render [_]
      (let [form (om/get-shared owner :flat-form)
            {:keys [dataset-id]} (om/get-shared owner)]
        (html
         [:div.right.rec-summary.rec-margin
          [:div#language-selector
           (when (f/multilingual? form)
             (om/build shared/language-selector nil))]
          [:div#data-status
           [:span.rec (pluralize-number num_of_submissions " Record")]]
          [:div.divider]
          (om/build dataview-actions dataset-id)])))))

(defmethod tabbed-dataview :default
  [app-state owner opts]
  (reify
    om/IInitState
    (init-state [_]
      (let [form (om/get-shared owner :flat-form)
            geopoints? (-> app-state :dataset-info :instances_with_geopoints)
            has-geodata? (if (some f/geopoint? form)
                           geopoints?
                           (some f/geofield? form))]
        {:active-view (if has-geodata? "map" "table")
         :no-geopoints? (not has-geodata?)}))
    om/IRenderState
    (render-state [_ {:keys [active-view no-geodata?]}]
      (let [view->display #(if (= active-view %) "block" "none")
            view->cls #(when (= active-view %) "clicked")
            activate-view! (fn [view]
                             (om/set-state! owner :active-view view)
                             (put! shared/event-chan {:re-render view}))
            dataviews (map dataview-map (-> app-state :views :all))
            dv->link (fn [{:keys [view label]}]
                       (if (and (= view "map") no-geodata?)
                         [:a {:class "inactive" :title "No geodata"} view]
                         [:a {:on-click (click-fn #(activate-view! view))
                              :href "#" :class (view->cls view)} label]))]
        (html
         [:div.tab-container.dataset-tabs
          [:div.tab-bar
           (map dv->link dataviews)
           (om/build dataview-infobar (-> app-state :dataset-info))]
          (for [{:keys [component view]} dataviews]
            [:div {:class (str "tab-page " view "-page")
                   :style {:display (view->display view)}}
             [:div.tab-content {:id (str "tab-content" view)}
              (om/build component app-state {:opts opts})]])])))))
