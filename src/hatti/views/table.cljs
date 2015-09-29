(ns hatti.views.table
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :refer [<! chan put! timeout]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [sablono.core :as html :refer-macros [html]]
            [hatti.constants :refer [_id _rank]]
            [hatti.ona.forms :as forms :refer [get-label format-answer]]
            [hatti.views :refer [table-page table-header table-search
                                 label-changer submission-view]]
            [hatti.views.record]
            [hatti.shared :as shared]
            [hatti.utils :refer [click-fn safe-regex]]
            [cljsjs.slickgrid-with-deps]))

;; DIVS
(def table-id "submission-grid")
(def pager-id "pager")

;; FIELDS
(defn get-extra-fields
   "Extra fields that will be displayed on the table."
  [is-filtered-dataview?]
  (let [extra-field  [{:full-name _rank :label "#" :name _rank :type "integer"}]]
    (if is-filtered-dataview?
      extra-field
      (conj extra-field forms/submission-time-field))))

(defn all-fields [form & {:keys [is-filtered-dataview?]}]
  "Given a (flat-)form, returns fields for table display.
   Puts extra fields in the beginning, metadata at the end of the table,
   and drops fields that have no data (eg. group/note)."
  (->> (concat (get-extra-fields is-filtered-dataview?)
               (forms/non-meta-fields form)
               (forms/meta-fields form :with-submission-details? (not is-filtered-dataview?)))
       (filter forms/has-data?)))

;; SLICKGRID HELPER FUNCTIONS

(defn compfn [args]
  "Comparator function for the slickgrid dataview.
   args.sortCol is the column being sorted, a and b are rows to compare.
   Claims a is bigger (return 1) if value for a > b, or a is null / empty."
  (fn [a b]
    (let [col (aget args "sortCol")
          lower #(if (string? %) (clojure.string/lower-case %) %)
          aval (lower (aget a (aget col "field")))
          bval (lower (aget b (aget col "field")))]
      (if (or (nil? aval) (> aval bval)) 1 -1))))

(defn filterfn [form item args]
  "Filter function for the slickgrid dataview, use as (partial filterfn form)
   item is the row, args contains query.
   Return value of false corresponds with exclusion, true with inclusion."
  (if-not args
    true ; don't filter anything if args is undefined
    (let [indexed-form (zipmap (map :full-name form) form)
          query (aget args "query")
          fmt-subitem (fn [[fname answer]]
                        (format-answer (get indexed-form fname) answer nil true))
          filtered (->> item
                        js->clj
                        (map fmt-subitem)
                        (map #(re-find (safe-regex query) (str %)))
                        (remove nil?))]
      (not (empty? filtered)))))

(defn formatter [field language row cell value columnDef dataContext]
  "Formatter for slickgrid columns takes row,cell,value,columnDef,dataContext.
   Get one with (partial formatter field language)."
  (let [clj-value (js->clj value :keywordize-keys true)]
    (forms/format-answer field clj-value language true)))

(defn- flat-form->sg-columns
   "Get a set of slick grid column objects when given a flat form."
  ([form] (flat-form->sg-columns form true))
  ([form get-label?] (flat-form->sg-columns form get-label? nil))
  ([form get-label? language & {:keys [is-filtered-dataview?]}]
   (clj->js
    (for [field (all-fields form :is-filtered-dataview? is-filtered-dataview?)]
      (let [{:keys [name type full-name]} field
            label (if get-label? (get-label field language) name)]
        {:id name :field full-name :type type
         :name label :toolTip label :sortable true
         :formatter (partial formatter field language)})))))

(defn- init-sg-pager [grid dataview]
  (let [Pager (.. js/Slick -Controls -Pager)]
    (Pager. dataview grid (js/jQuery (str "#" pager-id)))))

(def sg-options
  "Options to feed the slickgrid constructor."
  #js {:enableColumnReorder false
       :autoHeight true
       :rowHeight 24
       :enableTextSelectionOnCells true})

(defn sg-init [data form is-filtered-dataview?]
  "Creates a Slick.Grid backed by Slick.Data.DataView from data and fields.
   Most events are handled by slickgrid. On double-click, event is put on chan.
   Returns [grid dataview]."
  (let [columns (flat-form->sg-columns form true nil :is-filtered-dataview? is-filtered-dataview?)
        SlickGrid (.. js/Slick -Grid)
        DataView (.. js/Slick -Data -DataView)
        dataview (DataView.)
        grid (SlickGrid. (str "#" table-id) dataview columns sg-options)]
    ;; dataview / grid hookup
    (.subscribe (.-onRowCountChanged dataview)
                (fn [e args]
                  (.updateRowCount grid)
                  (.render grid)))
    (.subscribe (.-onRowsChanged dataview)
                (fn [e args]
                  (.invalidateRows grid (aget args "rows"))
                  (.render grid)))
    ;; sort / double-click handlers
    (.subscribe (.-onSort grid)
                (fn [e args]
                  (.sort dataview (compfn args) (aget args "sortAsc"))))
    (.subscribe (.-onDblClick grid)
                (fn [e args]
                  (let [rank (aget (.getItem dataview (aget args "row")) _rank)]
                    (put! shared/event-chan {:submission-to-rank rank}))))
    ;; page, filter, and data set-up on the dataview
    (init-sg-pager grid dataview)
    (.setPagingOptions dataview #js {:pageSize 25})
    (.setFilter dataview (partial filterfn form))
    (.setItems dataview (clj->js data) _id)
    [grid dataview]))

;; EVENT LOOPS

(defn handle-table-events
  [app-state grid dataview]
  "Event loop for the table view. Processes a tap of share/event-chan,
   and updates app-state/dataview/grid as needed."
  (let [event-chan (shared/event-tap)]
    (go
     (while true
       (let [e (<! event-chan)
             {:keys [submission-to-rank submission-clicked submission-unclicked
                     filter-by new-columns re-render]} e
             update-data! (partial om/update! app-state
                                   [:table-page :submission-clicked :data])]
         (when submission-to-rank
           (let [rank submission-to-rank
                 submission (-> (filter #(= rank (get % _rank))
                                        (get-in @app-state [:data]))
                                first)]
             (update-data! submission)))
         (when submission-clicked
           (update-data! submission-clicked))
         (when submission-unclicked
           (update-data! nil))
         (when new-columns
           (.setColumns grid new-columns)
           (.render grid))
         (when filter-by
           (.setFilterArgs dataview (clj->js {:query filter-by}))
           (.refresh dataview))
         (when (= re-render :table)
           ;; need tiny wait (~16ms requestAnimationFrame delay) to re-render table
           (go (<! (timeout 20))
               (.resizeCanvas grid)
               (.invalidateAllRows grid)
               (.render grid)
               (init-sg-pager grid dataview))))))))

;; OM COMPONENTS

(defmethod label-changer :default
  [_ owner]
  (reify
    om/IInitState
    (init-state [_] {:name-or-label :label})
    om/IRenderState
    (render-state [_ {:keys [name-or-label language]}]
      (let [options {:label [:span "Show: " [:strong "Label"]]
                     :name [:span "Show: " [:strong "Name"]]}
            {:keys [flat-form]} (om/get-shared owner)
            new-language (:current (om/observe owner (shared/language-cursor)))
            colset! #(put! shared/event-chan
                           {:new-columns
                            (flat-form->sg-columns flat-form
                                                   (= :label %)
                                                   new-language)})
            choose (fn [k] (om/set-state! owner :name-or-label k)
                           (colset! k))]
        (when (not= new-language language)
          (om/set-state! owner :language new-language)
          (colset! name-or-label))
        (html
         [:div {:class "drop-hover" :id "header-display-dropdown"}
          [:a {:href "#"}  (options name-or-label) [:i.fa.fa-angle-down]]
          [:ul {:class "submenu no-dot"}
           (for [[ok ov] options]
             [:li [:a {:on-click (click-fn #(choose ok)) :href "#"} ov]])]])))))

(defn delayed-search
  "Delayed search fires a query-event on event-chan if the value of the input
   doesn't change within 150 ms (ie, user is still typing).
   Call on-change or on-key-up, with (.-target event) as first argument."
  [input query-event-key]
  (let [query (.-value input)]
    ;; Wait 150 ms for input value to stabilize. Empirically felt good, plus
    ;; 200 ms wait is when people start noticing change in interfaces
    (go (<! (timeout 150))
        (when (= query (.-value input))
          (put! shared/event-chan {query-event-key query})))))

(defmethod table-search :default
  [_ owner]
  (om/component
   (html
    [:div.table-search
     [:i.fa.fa-search]
     [:input {:type "text"
              :placeholder "Search"
              :on-change #(delayed-search (.-target %) :filter-by)}]])))

(defmethod table-header :default
  [_ owner]
  (om/component
   (html
    [:div {:class "topbar"}
     [:div {:id pager-id}]
     (om/build label-changer nil)
     (om/build table-search nil)
     [:div {:style {:clear "both"}}]])))

(defn- init-grid!
  [data owner]
  "Initializes grid + dataview, and stores them in owner's state."
  (when (seq data)
    (let [{:keys [flat-form is-filtered-dataview?]} (om/get-shared owner)
          [grid dataview] (sg-init data flat-form is-filtered-dataview?)]
      (om/set-state! owner :grid grid)
      (om/set-state! owner :dataview dataview)
      [grid dataview])))

(defmethod table-page :default
  [app-state owner opts]
  "Om component for the table grid.
   Renders empty divs via om, hooks up slickgrid to these divs on did-mount."
  (reify
    om/IRenderState
    (render-state [_ _]
      (let [no-data? (empty? (get-in app-state [:data]))
            with-info #(merge % {:dataset-info (:dataset-info app-state)})]
        (html
         [:div.table-view
          (om/build submission-view
                    (with-info (get-in app-state [:table-page :submission-clicked]))
                    {:opts (merge (select-keys opts #{:delete-record! :role})
                                  {:view :table})})
          (if no-data?
            [:h3 "No Data"]
            (om/build table-header nil))
            [:div {:id table-id :class "slickgrid"}]])))
    om/IDidMount
    (did-mount [_]
      (let [data (get-in app-state [:data])]
        (when-let [[grid dataview] (init-grid! data owner)]
          (handle-table-events app-state grid dataview))))
    om/IWillReceiveProps
    (will-receive-props [_ next-props]
      "will-recieve-props resets slickgrid data if the table data has changed."
      (let [old-data (get-in (om/get-props owner) [:data])
            new-data (get-in next-props [:data])
            {:keys [grid dataview]} (om/get-state owner)]
        (when (not= old-data new-data)
          (if (empty? old-data)
            (when-let [[grid dataview] (init-grid! new-data owner)]
              (handle-table-events app-state grid dataview))
            (do ; data has changed
              (.invalidateAllRows grid)
              (.setItems dataview (clj->js new-data) _id)
              (.render grid))))))))
