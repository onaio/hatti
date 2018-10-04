(ns hatti.shared
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [chimera.js-interop :refer [format json->cljs]]
            [chimera.om.state :refer [merge-into-app-state! transact!]]
            [chimera.urls :refer [last-url-param]]
            [cljs.core.async :refer [<! chan mult tap]]
            [om.core :as om :include-macros true]
            [sablono.core :refer-macros [html]]
            [hatti.constants :refer [_rank _submission_time]]
            [hatti.ona.forms :as f]
            [hatti.utils.om.state :refer [update-app-state!]]))

;; (SHARED) EVENT CHANNELS

;; The way to get a channel that takes a single event that can be consumed
;; by multiple parties is to use tap / mult.
;; map / table / chart view event-channels are created as tapped copies of
;; `event-chan` (get a new own by calling `(event-tap)`).
;; Any event that is put onto the shared `event-chan` is copied onto all taps.
;; Taps can each also have local events that a given view both puts and takes.
(def event-chan (chan))
(def event-multi-chan (mult event-chan))
(def event-tap #(tap event-multi-chan (chan)))

;; Shared channel is `external-event-chan`, to get a new channel which can take
;; all the events put into `external-event-chan`, call `(external-event-tap)`
(def external-event-chan (chan))
(def external-event-multi-chan (mult external-event-chan))
(def external-event-tap #(tap external-event-multi-chan (chan)))

;; DATA
(def default-fields
  [{:full-name "_submission_time" :label "Submission Time"
    :name "_submission_time" :type "datetime"}])

(defn empty-app-state
  "An initial, empty, app-state, which can be modified to change dataviews."
  []
  (atom
   {:views {:all [:chart
                  :dashboard
                  :map
                  :overview
                  :photos
                  :report-view
                  :saved-charts
                  :settings
                  :table
                  :user-guide]
            :disabled []
            :selected :overview
            :settings {:all [:basemaps
                             :form-info
                             :integrated-apps
                             :media-files
                             :service-configuration
                             :settings
                             :xls-reports]
                       :active-tab :form-info
                       :integrated-apps {:active-section :apps-list
                                         :add? false}}}
    :map-page {:submission-clicked {:data nil}
               :geofield {}}
    :table-page {:submission-clicked {:data nil}
                 :hide-actions-column? false}
    :chart-page {:visible-charts default-fields
                 :chart-data {}}
    :dataset-info {}
    :data []
    :instance-ids-of-selected-rows []
    :selected-table-rows []
    :status {:total-records 0 :loading? true}
    :languages {:current nil :all []}
    :api-url "//api.ona.io/api/v1/"}))

;; HATTI global app-state
;; You can make replicas using (empty-app-state), but this is the only one
;; that the language-cursor is wired to
(def app-state (empty-app-state))

;; DATA UPDATERS

;; Replaces the `data` stored in the hatti app
(defn transact-app-data!
  "Given a function over data, run a transact on data inside app-state."
  [app-state transact-fn]
  (transact! app-state [:data] transact-fn))

(defn update-app-data!
  "Given `data` received from the server, update the app-state.
   Sorts by submission time, and adds rank to the data, for table + map views."
  [app-state data & {:keys [rerank? completed? sort-field current-start-index]
                     :or {current-start-index 0}}]
  (let [add-rank (fn [i v]
                   (assoc v _rank
                          (+ current-start-index
                             (inc i))))
        data (if (and rerank? (seq data))
               (->> data
                    (sort-by #(get % (or sort-field _submission_time)))
                    (map-indexed add-rank) vec)
               (->> data
                    (map-indexed add-rank) vec))
        total-records (count data)]
    (transact-app-data! app-state (fn [_] data))
    (merge-into-app-state! app-state [:status]
                           {:total-records total-records
                            :loading? (not completed?)})))

(defn add-to-app-data!
  "Add to app data."
  [app-state data & {:keys [completed?]}]
  (let [old-data (:data @app-state)]
    ;; only re-rank if loading is completed
    (update-app-data! app-state (concat old-data data) :rerank? completed?)
    (update-app-state! app-state [:status :loading?] (not completed?))))

;; LANGUAGE

(defn maybe-merge-languages!
  "Merge languages into the app-state if form is multilingual"
  [form & [state]]
  (when (f/multilingual? form)
    (let [langs (f/get-languages form)
          default-lang (f/default-lang langs)
          lang-state {:all langs
                      :default default-lang
                      :current default-lang}]
      (merge-into-app-state!
       (or state app-state)
       [:languages] lang-state))))

(defn language-cursor [& [state]]
  (om/ref-cursor (:languages (om/root-cursor (or state app-state)))))

(defn language-selector
  "A language selector and a following divider."
  [_ owner {custom-app-state :app-state}]
  (reify
    om/IRender
    (render [_]
      (let [{:keys [current all] :as ls}
            (om/observe owner (language-cursor (or custom-app-state
                                                   app-state)))
            get-update-handler (fn [language]
                                 (fn [event]
                                   (om/update! ls [:current] language)
                                   (.preventDefault event)))
            stringify #(if (keyword? %) (name %) (str %))]
        (html
         [:div.language-selector-inner
          [:span.dropdown.drop-hover
           [:i.fa.fa-globe {:style {:margin-right ".2em"}}]
           [:span
            (if (f/english? current) "EN" (stringify current))]
           [:i.fa.fa-angle-down {:style {:margin-left ".5em"}}]
           [:ul.submenu
            (for [language all]
              [:li [:a {:href "#" :on-click (get-update-handler language)}
                    (stringify language)]])]]
          [:div.divider]])))))
