(ns hatti.views
  (:require [om.core :as om]))

;; GENERIC DISPATCHER
(def view-type-dispatcher
  (fn [_ owner & args] (om/get-shared owner :view-type)))

;; TABBED DATAVIEW
(defmulti tabbed-dataview view-type-dispatcher)
(defmulti dataview-infobar view-type-dispatcher)
(defmulti dataview-actions view-type-dispatcher)

;; MAP
(defmulti map-page view-type-dispatcher)
(defmulti map-geofield-chooser view-type-dispatcher)
(defmulti map-and-markers view-type-dispatcher)
(defmulti map-record-legend view-type-dispatcher)

(defmulti map-viewby-legend view-type-dispatcher)
(defmulti map-viewby-menu view-type-dispatcher)

(defmulti map-viewby-answer-legend view-type-dispatcher)
(defmulti map-viewby-answer-close view-type-dispatcher)
(defmulti viewby-answer-list view-type-dispatcher)
(defmulti viewby-answer-list-filter view-type-dispatcher)

;; TABLE
(defmulti table-page view-type-dispatcher)
(defmulti table-header view-type-dispatcher)
(defmulti table-search view-type-dispatcher)
(defmulti label-changer view-type-dispatcher)

;; MAP TABLE
(defmulti map-table-page view-type-dispatcher)

;; CHART
(defmulti chart-page view-type-dispatcher)
(defmulti chart-chooser view-type-dispatcher)
(defmulti single-chart view-type-dispatcher)
(defmulti list-of-charts view-type-dispatcher)

;; INDIVIDUAL RECORDS
(defmulti submission-view view-type-dispatcher)
(defmulti print-xls-report-btn view-type-dispatcher)
(defmulti repeat-view view-type-dispatcher)
(defmulti edit-delete view-type-dispatcher)

;; DETAILS PAGE
(defmulti settings-page view-type-dispatcher)
(defmulti form-details view-type-dispatcher)

;; OVERVIEW PAGE
(defmulti overview-page view-type-dispatcher)

;; PHOTOS PAGE
(defmulti photos-page view-type-dispatcher)

;; SAVED CHARTS PAGE - temporary
(defmulti saved-charts-page view-type-dispatcher)

;; USER GUIDE PAGE
(defmulti user-guide-page view-type-dispatcher)
