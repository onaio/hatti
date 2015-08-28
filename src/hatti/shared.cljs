(ns hatti.shared
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :refer [<! chan mult tap]]
            [om.core :as om :include-macros true]
            [sablono.core :refer-macros [html]]
            [hatti.constants :refer [_rank]]
            [hatti.ona.forms :as f]
            [hatti.utils :refer [json->cljs format last-url-param]]))

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
   {:views {:all [:overview :map :table :chart :settings]
            :selected :overview}
    :map-page {:submission-clicked {:data nil}
               :geofield {}}
    :table-page {:submission-clicked {:data nil}}
    :chart-page {:visible-charts default-fields
                 :chart-data {}}
    :dataset-info {}
    :data []
    :languages {:current nil :all []}}))

;; HATTI global app-state
;; You can make replicas using (empty-app-state), but this is the only one
;; that the language-cursor is wired to
(def app-state (empty-app-state))

;; DATA UPDATERS

;; A transact! function that optionally uses swap! if this atom
;; has not been yet made into a root cursor by om.
(defn transact!
  [app-state]
  (if (satisfies? om/ITransact app-state) om/transact! swap!))

;; replicating the cursor semantics of om/transact!
;; the ks allows you to "zoom into" a cursor
(defn transact-app-state!
  [app-state ks transact-fn]
  ((transact! app-state) app-state #(update-in % ks transact-fn)))

;; Replaces the `data` stored in the hatti app
(defn transact-app-data!
  "Given a function over data, run a transact on data inside app-state."
  [app-state transact-fn]
  (transact-app-state! app-state [:data] transact-fn))

(defn update-app-data!
  "Given `data` received from the server, update the app-state.
   Sorts by submission time, and adds rank to the data, for table + map views."
  [app-state data & {:keys [rerank?]}]
  (let [data (if (and rerank? (seq data))
               (->> data
                    (sort-by #(get % "_submission_time"))
                    (map-indexed (fn [i v] (assoc v _rank (inc i))))
                    vec)
               data)
        num_of_submissions (count data)]
    (transact-app-data! app-state (fn [_] data))
    (when-not (zero? num_of_submissions)
      (transact-app-state! app-state
                           [:dataset-info :num_of_submissions]
                           (fn [_] num_of_submissions)))))

(defn add-to-app-data!
  "Add to app data."
  [app-state data & {:keys [completed?]}]
  (let [old-data (:data @app-state)]
    ;; only re-rank if loading is completed
    (update-app-data! app-state (concat old-data data) :rerank? completed?)
    (transact-app-state! app-state [:dataset-info :loading?] #(not completed?))))

;; LANGUAGE

(defn language-cursor []
  (om/ref-cursor (:languages (om/root-cursor app-state))))

(defn language-selector
   "A language selector and a following divider."
   [_ owner]
   (reify
     om/IRender
     (render [_]
       (let [{:keys [current all] :as ls} (om/observe owner (language-cursor))
             update-current #(om/update! ls [:current] %)
             stringify #(if (keyword? %) (name %) (str %))]
       (html
        [:div {:class "language-selector-inner"}
         [:span {:class "dropdown drop-hover"}
          [:i {:class "fa fa-globe" :style {:margin-right ".2em"}}]
          [:span
           (if (f/english? current) "EN" (stringify current))]
          [:i {:class "fa fa-angle-down" :style {:margin-left ".5em"}}]
          [:ul {:class "submenu"}
           (for [l all]
             [:li [:a {:href "#" :on-click #(update-current l)}
                   (stringify l)]])]]
         [:div {:class "divider"}]])))))
