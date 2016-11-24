(ns hatti.views.dataview
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [chimera.js-interop :refer [format]]
            [chimera.om.state :refer [transact! merge-into-app-state!]]
            [cljs.core.async :refer [put!]]
            [om.core :as om :include-macros true]
            [sablono.core :as html :refer-macros [html]]
            [hatti.constants :refer [google-sheets]]
            [hatti.ona.forms :as f]
            [hatti.shared :as shared]
            [hatti.views :refer [tabbed-dataview
                                 dataview-infobar dataview-actions
                                 map-page table-page chart-page settings-page
                                 photos-page overview-page saved-charts-page]]
            [hatti.views.photos]
            [hatti.views.map]
            [hatti.views.table]
            [hatti.views.chart]
            [hatti.views.settings]
            [hatti.views.overview]
            [hatti.views.saved-charts]
            [hatti.utils :refer [click-fn pluralize-number]]))

(def dataview-map
  {:overview {:view :overview
              :label "Overview"
              :component overview-page}
   :map {:view :map
         :label "Map"
         :component map-page}
   :table {:view :table
           :label "Table"
           :component table-page}
   :photos {:view :photos
            :label "Photos"
            :component photos-page}
   :chart {:view :chart
           :label "Charts"
           :component chart-page}
   :saved-charts {:view :saved-charts
                  :label "Dashboard"
                  :component saved-charts-page}
   :settings {:view :settings
              :label "Settings"
              :component settings-page}})

(defn- view->inactive-tab
  "Build and set the tooltips for inactive views based on why they are
   inactive."
  [view label is-encrypted? not-within-pricing-limits?]
  [:a.inactive
   (str label " ")
   [:span.tooltip
    [:span.tip-info.tooltip
     (cond
       is-encrypted? "This tab is disabled because this form is encrypted."
       not-within-pricing-limits?
       "This tab is disabled because you are over the pricing limits."
       :else
       (condp = view
         :map (str "The Map tab is disabled because this form "
                   "has no location questions.")
         :photos (str "The Photos tab is disabled because this form has no "
                      "photo questions.")
         :table (str "The table tab is disabled because you do not have "
                     "permission to view it.")
         :chart (str "The charts tab is disabled because you do not have "
                     "permission to view it.")
         :saved-charts (str "The dashboards tab is disabled because "
                            "you do not have permission to view it.")
         "This tab is disabled."))]
    [:span.tip-question "?"]]])

(defmethod dataview-actions :default
  [cursor owner]
  (om/component (html nil)))

(defmethod dataview-infobar :default
  [{:keys [dataset-info status]} owner]
  (reify
    om/IWillMount
    (will-mount [_]
      (shared/maybe-merge-languages! (om/get-shared owner :flat-form)))
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

(defn activate-view!
  "Strip off ampersand suffixes then switch to view if result is a valid view"
  [view]
  (let [view (keyword (or (last (re-find #"(.*?)&" view)) view))
        views (-> @shared/app-state :views :all)]
    (when (contains? (set views) view)
      (merge-into-app-state! shared/app-state
                             [:views]
                             {:selected view})
      (put! shared/event-chan {:re-render view}))))

(defn activate-settings-view! [view settings-section]
  (let [view (keyword view)
        settings-section (keyword settings-section)
        views (-> @shared/app-state :views :all)
        settings-views (-> @shared/app-state :views :settings :all)]
    (when (contains? (set settings-views) settings-section)
      (merge-into-app-state! shared/app-state
                             [:views]
                             {:selected view})
      (merge-into-app-state! shared/app-state
                             [:views :settings]
                             {:active-tab settings-section})
      (put! shared/event-chan {:re-render view}))))

(defn activate-integrated-apps-view! [view settings-section app-type]
  (let [view (keyword view)
        settings-section (keyword settings-section)
        settings-views (-> @shared/app-state :views :settings :all)]
    (when (contains? (set settings-views) settings-section)
      (transact!
       shared/app-state
       (fn [app-state]
         (-> app-state
             (assoc-in [:views :selected] view)
             (assoc-in [:views :settings :active-tab] settings-section)
             (assoc-in [:views :settings :integrated-apps :active-section]
                       app-type))))
      (if (= app-type google-sheets)
        (merge-into-app-state! shared/app-state
                               [:views :settings :integrated-apps]
                               {:add? true}))
      (put! shared/event-chan {:re-render view}))))

(defmethod tabbed-dataview :default
  [app-state owner opts]
  (reify
    om/IRender
    (render [_]
      (let [{:keys [is-encrypted? is-within-pricing-limits?]
             {:keys [active selected]} :views} app-state
            view->display #(if (= selected %) "block" "none")
            view->cls #(when (= selected %) "clicked")
            dataviews (->> app-state :views :all
                           (map dataview-map) (remove nil?))
            dv->link (fn [{:keys [view label]}]
                       (let [active? (some #(= view %) active)]
                         (if active?
                           [:a {:class (view->cls view)
                                :href (str "#/" (name view))} label]
                           (view->inactive-tab view
                                               label
                                               is-encrypted?
                                               is-within-pricing-limits?))))]
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
             (when (= selected view)
               [:div.tab-content {:id (str "tab-content" (name view))}
                (om/build component app-state {:opts opts})])])])))))
