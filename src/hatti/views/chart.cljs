(ns hatti.views.chart
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :refer [<! chan put!]]
            [om.core :as om :include-macros true]
            [sablono.core :as html :refer-macros [html]]
            [hatti.ona.forms :as forms]
            [hatti.charting :refer [make-chart]]
            [hatti.shared :as shared]
            [hatti.views :refer [chart-page chart-chooser
                                 list-of-charts single-chart]]
            [hatti.utils :refer [click-fn]]))

;; EVENT HANDLERS

(defn- handle-chart-events
  [app-state chart-get]
  (let [event-chan (shared/event-tap)]
    (go
      (while true
        (let [e (<! event-chan)
              {:keys [add-chart remove-chart field]} e
              field-name (:name field)
              ks [:chart-page :chart-data field-name]]
          (when add-chart
            (om/transact! app-state [:chart-page :visible-charts]
                          #(vec (distinct (cons field %))))
            (when-not (get-in ks @app-state)
              (go (let [data (:body (<! (chart-get field-name)))]
                    (om/update! app-state ks data)))))
          (when remove-chart
            (om/transact! app-state
                          [:chart-page :visible-charts]
                          (fn [vcs] (remove #(= field %) vcs)))))))))

;; DOM BUILDING HELPERS

(defn- field->link
  [field language]
  (let [flabel (forms/get-label field language)
        attrs (merge
               (when (forms/group? field)
                 {:class "drop-subheader"})
               (when (or (forms/categorical? field)
                         (forms/numeric? field)
                         (forms/time-based? field))
                 {:href "#"
                  :on-click (click-fn #(put! shared/event-chan
                                             {:field field
                                              :add-chart true}))}))]
    (when-not (or (nil? flabel) (= "" flabel))
      [:li {:class "submenu-list"}
       [:a attrs (forms/get-icon field) flabel]])))

;; OM COMPONENTS

(defmethod chart-chooser :default
  [cursor owner]
  "The chart chooser om component."
  (reify
    om/IRender
    (render [_]
      (let [form (om/get-shared owner [:flat-form])
            language (:current (om/observe owner (shared/language-cursor)))
            to-links (partial map #(field->link % language))]
        (html [:div.cfix#chart-chooser
               [:span "Add New Chart"]
               [:div.drop-hover.dropdown-single#chart-dropdown
                [:a.pure-button.btn-border.t-red {:href "#"}
                 "Select Field "]
                [:ul#chart-dropdown-menu.submenu.no-dot
                 [:li [:span.drop-header "Metadata"]]
                 (to-links (forms/meta-fields form
                                              :with-submission-details? true))
                 [:li [:span.drop-header "Questions"]]
                 (to-links (forms/non-meta-fields form))]]])))))

(defmethod single-chart :default
  [cursor owner {:keys [chart-get field]}]
  "A single chart component."
  (reify
    om/IInitState
    (init-state [_]
      {:status :active})
    om/IRenderState
    (render-state [_ {:keys [status]}]
      (let [{:keys [data]} cursor
            language (:current (om/observe owner (shared/language-cursor)))]
        (html
         (if (= status :inactive)
           [:div]
           [:div.chart-holder
            [:a.btn-close.right
             {:on-click (click-fn
                         #(put! shared/event-chan
                                {:field field :remove-chart true}))
              :href "#"} "×"]
            [:div.chart-content
             [:h3.chart-name (forms/get-label field language)]
             [:div.bar-chart
              (if data
                (-> data (make-chart language) :chart)
                [:div [:i.fa.fa-cog.fa-spin]])]]]))))))

(defmethod list-of-charts :default
  [cursor owner]
  "The list of charts."
  (reify
    om/IRender
    (render [_]
      (html
       [:div.charts.clearfix
        (for [field (:visible-charts cursor)]
          (om/build single-chart
                    {:data (get-in cursor [:chart-data (:name field)])}
                    {:opts {:field (om/value field)}}))]))
    om/IDidMount
    (did-mount [_]
      (doseq [field (:visible-charts cursor)]
        (put! shared/event-chan {:add-chart true :field (om/value field)})))))

(defmethod chart-page :default
  [cursor owner {:keys [chart-get]}]
  "The overall chart-page om component."
  (reify
    om/IWillMount
    (will-mount [_]
      (handle-chart-events cursor chart-get))
    om/IRender
    (render [_]
      (html
       [:div.charts-ui
        (om/build chart-chooser nil)
        (om/build list-of-charts (:chart-page cursor))]))))
