(ns hatti.views.dataview
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :refer [put!]]
            [om.core :as om :include-macros true]
            [sablono.core :as html :refer-macros [html]]
            [hatti.constants :refer [mapping-threshold]]
            [hatti.ona.forms :as f]
            [hatti.shared :as shared]
            [hatti.utils.om.state :refer [merge-into-app-state!]]
            [hatti.views :refer [tabbed-dataview
                                 dataview-infobar dataview-actions
                                 map-page map-table-page table-page chart-page 
                                 settings-page photos-page overview-page
                                 saved-charts-page user-guide-page]]
            [hatti.views.photos]
            [hatti.views.map]
            [hatti.views.map-table]
            [hatti.views.table]
            [hatti.views.chart]
            [hatti.views.settings]
            [hatti.views.overview]
            [hatti.views.saved-charts]
            [hatti.views.user-guide]
            [hatti.utils :refer [click-fn format pluralize-number]]))

(def dataview-map
  {:overview {:view :overview
              :label "Overview"
              :component overview-page}
   :photos {:view :photos
            :label "Photos"
            :component photos-page}
   :map {:view :map
         :label "Map"
         :component map-page}
   :table {:view :table
           :label "Table"
           :component table-page}
   :chart {:view :chart
           :label "Charts"
           :component chart-page}
   :map-table {:view :map-table
               :label "Field Monitoring Report"
               :component map-table-page}
   :saved-charts {:view :saved-charts
                  :label "Dashboard"
                  :component saved-charts-page}
   :settings {:view :settings
              :label "Settings"
              :component settings-page}
   :user-guide {:view :user-guide
                :label "User Guide"
                :component user-guide-page}})

(defmethod dataview-actions :default
  [cursor owner]
  (om/component (html nil)))

(defmethod dataview-infobar :default
  [{:keys [dataset-info status]} owner]
  (reify
    om/IWillMount
    (will-mount [_]
      (let [form (om/get-shared owner :flat-form)
            langs (f/get-languages form)
            default-lang (f/default-lang langs)
            lang-state {:all langs
                        :default default-lang
                        :current default-lang}]
        (when (f/multilingual? form)
          (merge-into-app-state! shared/app-state [:languages] lang-state))))
    om/IRender
    (render [_]
      (let [form (om/get-shared owner :flat-form)
            {:keys [dataset-id]} (om/get-shared owner)
            {:keys [num_of_submissions]} dataset-info
            {:keys [loading? total-records]} status]
        (html
         [:div.right.rec-summary.rec-margin
          [:div#language-selector
           (when (f/multilingual? form)
             (om/build shared/language-selector nil))]
          [:div#data-status
           [:span.rec
            (when loading? [:i.fa.fa-spinner.fa-pulse])
            (pluralize-number num_of_submissions " Record")]]
          [:div.divider]
          (om/build dataview-actions dataset-id)])))))

(defn activate-view! [view]
  (let [view (keyword view)
        views (-> @shared/app-state :views :all)]
    (when (contains? (set views) view)
      (merge-into-app-state! shared/app-state
                             [:views]
                             {:selected view})
      (put! shared/event-chan {:re-render view}))))

(defmethod tabbed-dataview :default
  [app-state owner opts]
  (reify
    om/IInitState
    (init-state [_]
      (let [form (om/get-shared owner :flat-form)
            geopoints? (-> app-state :dataset-info :instances_with_geopoints)
            no-geodata? (not (or geopoints?
                                 (some f/geopoint? form)
                                 (some f/geofield? form)))]
        (when (and no-geodata? (= :map (-> app-state :views :selected)))
          (om/update! app-state [:views :selected] :table))
        {:no-geodata? no-geodata?}))
    om/IRenderState
    (render-state [_ {:keys [no-geodata?]}]
      (let [active-view (-> app-state :views :selected)
            view->display #(if (= active-view %) "block" "none")
            view->cls #(when (= active-view %) "clicked")
            dataviews (->> app-state :views :all
                           (map dataview-map) (remove nil?))
            dv->link (fn [{:keys [view label]}]
                       (let [active? (some #(= view %) (-> app-state
                                                           :views :active))]
                         (cond
                           (and (= view :map) no-geodata?)
                           [:a {:class "inactive" :title "No geodata"}
                            (name view)]
                           (and (= view :map)
                                (> (-> app-state
                                       :dataset-info
                                       :num_of_submissions)
                                   mapping-threshold))
                           [:a
                            {:class "inactive"
                             :title (format
                                     "Map does not support more than %d points."
                                     mapping-threshold)}
                            (name view)]
                           :else
                           [:a
                            {:href (when active? (str "#/" (name view)))
                             :class (if active? (view->cls view)
                                        "inactive")} label])))]
        (html
         [:div.tab-container.dataset-tabs
          [:div.tab-bar
           (map dv->link dataviews)
           (om/build dataview-infobar
                     {:dataset-info (-> app-state :dataset-info)
                      :status (-> app-state :status)})]
          (for [{:keys [component view]} dataviews]
            [:div {:class (str "tab-page " (name view) "-page")
                   :style {:display (view->display view)}}
             (when (= active-view view)
               [:div.tab-content {:id (str "tab-content" (name view))}
                (om/build component app-state {:opts opts})])])])))))
