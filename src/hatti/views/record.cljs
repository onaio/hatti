(ns hatti.views.record
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [clojure.string :as string]
            [cljs.core.async :refer [<! chan put!]]
            [om.core :as om :include-macros true]
            [sablono.core :as html :refer-macros [html]]
            [cljsjs.moment]
            [hatti.constants :refer [_rank]]
            [hatti.views :refer [submission-view repeat-view
                                 edit-delete print-xls-report-btn]]
            [hatti.ona.forms :as f]
            [hatti.shared :as shared]
            [hatti.utils :refer [click-fn format last-url-param
                                 pluralize-number]]))

;; Helper functions to easily create sablono / hiccup vectors

(defn wrap-with [root]
  "Wrap-with produces a partial to wrap a set of children.
   ((wrap-with :div) 1 2 3) => [:div [1 2 3]]
   ((wrap-with [:div {:class 0}]) 1 2 3) => [:div {:class 0} [1 2 3]]"
  (fn [& children]
    (if (keyword? root)
      (conj [root] children)
      (conj root children))))

;; Helper functions to help render buttons, etc.

(defn submission-arrow
  [dir cur-rank]
  (let [icon (str "fa fa-arrow-" (name dir))
        new-rank ((case dir :left dec :right inc) cur-rank)]
    [:a {:on-click (click-fn
                    #(put! shared/event-chan {:submission-to-rank new-rank}))
         :class "pure-button" :href "#"} [:i {:class icon}]]))

(defn submission-closer []
  [:a {:on-click (click-fn
                  #(put! shared/event-chan {:submission-unclicked true}))
       :class "btn-close right" :href "#"} "×"])

(defmethod print-xls-report-btn :default
  [cursor owner]
  (om/component
   (html [:div {:id "print-xls-report"}])))

;; A single row of a question / answer pair
(def qa-elements
  {:map {:row-el :div.question-answer
         :question-el :span.question
         :answer-el :span.answer}
   :table {:row-el :tr.question-answer
           :question-el :td.question
           :answer-el :td.answer}})

(defn format-as-question-answer
  [view field data lang]
  (let [fname (:full-name field)
        flabel (f/get-label field lang)
        answer (get data fname)
        alabel (f/format-answer field answer lang)
        {:keys [row-el question-el answer-el]} (qa-elements view)]
      (when (and alabel (not= alabel f/no-answer))
        (if (f/repeat? field)
          (om/build repeat-view
                    {:data answer :repeat-field field :lang lang}
                    {:opts {:view view}})
          [row-el
           [question-el flabel] [answer-el alabel]]))))

;; The whole submission view

(def submission-elements
  {:map {:top-level-wrap (wrap-with :div.legend.infobar.submission.top.right)
         :topbar-wrap (wrap-with :div.border-bottom.legend-topbar)
         :header-wrap (wrap-with :div.border-bottom.submission-header)
         :submission-info-wrap (wrap-with :div.submission.info-scroll)
         :section-wrap (wrap-with :div.border-bottom)
         :h4-cls "t-red"}
   :table {:top-level-wrap (wrap-with :div.widget-info#single-submission-info)
           :topbar-wrap (wrap-with :div.topbar)
           :header-wrap (wrap-with :div.header)
           :submission-info-wrap (fn [& tbody]
                                   [:div.submission
                                    [:table.pure-table.pure-table-bordered
                                     [:thead
                                       [:tr [:th "Question"] [:th "Response"]]]
                                     [:tbody tbody]]])
           :section-wrap identity
           :show-instance-id true
           :h4-cls ""}})

(defmethod repeat-view :default
  [{:keys [data repeat-field lang]} owner {:keys [view]}]
  "Renders data for a repeat field, which is complex; repeat fields are subforms.
   data is expected to be an vector of repeated data, in each element of which
   we expect data keyed by one of the :children fields of the repeat
   lang is language (expected to change), view is either :map or :table."
  (reify
    om/IInitState
    (init-state [_] {:collapsed? true})
    om/IRenderState
    (render-state [_ {:keys [:collapsed?]}]
      (when (seq data)
        (let [collapse! #(om/set-state! owner :collapsed? %)
              {:keys [row-el question-el answer-el]} (qa-elements view)
              header [row-el
                      [question-el (f/get-label repeat-field lang)]
                      [answer-el
                       (str (pluralize-number (count data) " Repeat") " - ")
                       (if collapsed?
                         [:a {:on-click (click-fn #(collapse! false))
                              :href "#"} "Show Repeats"]
                         [:a {:on-click (click-fn #(collapse! true))
                              :href "#"} "Hide Repeats"])]]
              fname (:full-name repeat-field)
              render-child (fn [d]
                             (for [fld (:children repeat-field)]
                               (format-as-question-answer view fld d lang)))
              tbl (fn [cls & body]
                      [:tr {:class cls}
                       [:td.no-pad {:col-span 2}
                        [:table {:class cls} [:tbody body]]]])]
          (html
           (if collapsed?
             header
             (case view
               :map [:div.repeat-block
                     header
                     [:ol.repeat
                      (for [d data] [:li (render-child d)])]]
               :table (tbl "repeat-block"
                           header
                           (for [d data]
                             [:tbody.repeat (render-child d)]))))))))))

(defn header-note
  [view {:keys [geofield data]}]
  (when (and (= view :map)
             (not (get data (:full-name geofield))))
    [:span.no-geo.t-normal.right "No geodata"]))

(defmethod edit-delete :default
  [instance-id owner {:keys [delete-record!]}]
  (om/component
   (html nil)))

(defmethod submission-view :default
  [cursor owner {:keys [view] :as opts}]
    (reify
      om/IInitState
      (init-state [_] {:expand-meta? false})
      om/IRenderState
      (render-state [_ {:keys [expand-meta?]}]
        (let [form (om/get-shared owner [:flat-form])
              language (:current (om/observe owner (shared/language-cursor)))
              {:keys [data dataset-info]} cursor
              cur-rank (get data _rank)
              instance-id (get data "_id")
              sdatetime (js/moment (get data "_submission_time"))
              {:keys [top-level-wrap topbar-wrap header-wrap section-wrap
                      submission-info-wrap h4-cls]} (submission-elements view)
              mapview? (= view :map)
              tableview? (= view :table)]
          (html
           (when data
             (top-level-wrap
              (topbar-wrap
               (when mapview? (submission-arrow :left cur-rank))
               (when mapview? (submission-arrow :right cur-rank))
               (om/build print-xls-report-btn {:instance-id instance-id
                                               :dataset-info dataset-info})
               (submission-closer))
              (header-wrap
               [:h4 {:class h4-cls} (str "Submission " cur-rank)
                (header-note view cursor)]
               [:p
                (str "Submitted at " (.format sdatetime "LT")
                        " on " (.format sdatetime "ll")) [:br]
                [:span (str "Record ID: " instance-id)]
                (om/build edit-delete instance-id {:opts opts})
                [:span {:class "expand-meta right"}
                 [:a {:href "#"
                      :on-click (click-fn
                                 #(om/update-state! owner :expand-meta? not))}
                  (if expand-meta? "Hide Metadata" "Show Metadata")]]])
              ;; actual submission data, inside a div.info-scroll
              (submission-info-wrap
               (when expand-meta?
                 (section-wrap
                  (for [q (f/meta-fields form :with-submission-details? true)]
                    (format-as-question-answer view q data language))))
               (section-wrap
                (for [q (f/non-meta-fields form)]
                  (format-as-question-answer view q data language)))))))))))
