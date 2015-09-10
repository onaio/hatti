(ns hatti.views.map
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :refer [<! chan put! timeout]]
            [om.core :as om :include-macros true]
            [sablono.core :as html :refer-macros [html]]
            [hatti.constants :refer [_id _rank]]
            [hatti.ona.forms :as f :refer [format-answer get-label get-icon]]
            [hatti.utils :refer [click-fn]]
            [hatti.map.viewby :as vb]
            [hatti.map.utils :as mu]
            [hatti.shared :as shared]
            [hatti.views :as views
             :refer [map-page map-and-markers map-geofield-chooser
                     map-record-legend submission-view
                     map-viewby-legend map-viewby-menu
                     viewby-answer-list viewby-answer-list-filter
                     map-viewby-answer-legend map-viewby-answer-close]]
            [hatti.views.record]))


;;;;; EVENT HANDLERS

(defn handle-viewby-events
  "Listens to view-by events, and change the map-cursor appropriately.
   Needs access to app-state, event channels, as well as map objects."
  [app-state {:keys [get-id-marker-map]}]
  (let [event-chan (shared/event-tap)]
    (go
     (while true
       (let [e (<! event-chan)
             {:keys [view-by view-by-closed view-by-filtered]} e
             markers (vals (get-id-marker-map))]
         (when view-by
           (let [{:keys [full-name] :as field} (:field view-by)
                 ids (map #(get % _id) (:data @app-state))
                 raw-answers (map #(get % full-name) (:data @app-state))
                 vb-info (vb/viewby-info field raw-answers ids)]
             (om/update! app-state [:map-page :view-by] vb-info)
             (vb/view-by! vb-info markers)))
         (when view-by-filtered
           (vb/view-by! (get-in @app-state [:map-page :view-by]) markers))
         (when view-by-closed
           (om/update! app-state [:map-page :view-by] {})
           (mu/clear-all-styles markers)))))))

(defn handle-submission-events
  "Listens to sumission events, and change the map-cursor appropriately.
   Needs access to app-state, event channels, as well as map objects."
  [app-state {:keys [get-id-marker-map]}]
  (let [event-chan (shared/event-tap)]
    (go
     (while true
       (let [e (<! event-chan)
             {:keys [submission-to-rank submission-unclicked]} e
             prev-marker (get-in @app-state [:map-page :submission-clicked :marker])]
         (when submission-unclicked
           (om/update! app-state [:map-page :submission-clicked]
                       {:data nil :prev-marker prev-marker}))
         (when submission-to-rank
           (let [rank submission-to-rank
                 new-data (-> (filter
                               #(= rank (get % _rank))
                               (:data @app-state))
                              first)]
             (om/update! app-state [:map-page :submission-clicked]
                         {:data new-data
                          :marker (get (get-id-marker-map)
                                       (get new-data _id))
                          :prev-marker prev-marker}))))))))

(defn handle-data-updates
  "Fires events that need to be re-fired when data updates."
  [app-state]
  (let [event-chan (shared/event-tap)]
    (go
     (while true
       (let [{:keys [data-updated] :as e} (<! event-chan)]
         (when data-updated
           (put! shared/event-chan
                 {:submission-to-rank
                  (get-in @app-state [:map-page :submission-clicked :data _rank])})
           (put! shared/event-chan
                 {:view-by (get-in @app-state [:map-page :view-by])})))))))

(defn handle-re-render
  "Handles the re-render event"
  [app-state {:keys [re-render!]}]
  (let [event-chan (shared/event-tap)]
    (go
     (while true
       (let [{:keys [re-render] :as e} (<! event-chan)]
         (when (= re-render :map)
           (go (<! (timeout 16))
               (re-render!))))))))

(defn handle-map-events
  "Creates multiple channels and delegates events to them."
  [app-state opts]
  (handle-viewby-events app-state opts)
  (handle-submission-events app-state opts)
  (handle-re-render app-state opts)
  (handle-data-updates app-state))

;;;;; OM COMPONENTS

(defmethod map-viewby-menu :default
  [{:keys [num_of_submissions]} owner]
  (reify
    om/IRender
    (render [_]
      (let [form (om/get-shared owner [:flat-form])
            no-data? (zero? num_of_submissions)
            language (:current (om/observe owner (shared/language-cursor)))
            fields (filter #(or (f/categorical? %)
                                (f/numeric? %)
                                (f/text? %)
                                (f/time-based? %)
                                (f/calculate? %))
                               (f/non-meta-fields form))]
        (html
        [:div {:class "legend viewby top left"}
         [:div {:class "drop-hover" :id "viewby-dropdown"}
          [:a {:class "pure-button" :href "#"} "View By"
           [:i {:class "fa fa-angle-down"
                :style {:margin-left ".5em"}}]]
          [:ul {:class "submenu no-dot" :style {:width "600px"}}
           (if no-data?
             [:h4 "No data"]
             (if (empty? fields)
               [:h4 "No questions of type select one"]
               (for [{:keys [name] :as field} fields]
                 [:li
                  [:a {:on-click (click-fn #(put! shared/event-chan
                                                  {:view-by {:field field}}))
                       :href "#" :data-question-name name}
                       (get-icon field) (get-label field language)]])))]]])))))

(defmethod map-viewby-answer-close :default
  [_ owner]
  (om/component
   (html [:a {:class "btn-close right" :href "#"
              :on-click
              (click-fn
               #(put! shared/event-chan {:view-by-closed true}))} "×"])))

(defmethod viewby-answer-list-filter :default
  [cursor owner]
  (om/component
   (let [{:keys [answers field]} cursor
         language (:current (om/observe owner (shared/language-cursor)))
         filter! (fn [query]
                   (let [{:keys [visible-answers answer->selected?]}
                           (vb/filter-answer-data-structures answers query field language)]
                     (om/update! cursor :visible-answers visible-answers)
                     (om/update! cursor :answer->selected? answer->selected?))
                   (put! shared/event-chan {:view-by-filtered true}))]
     (html
      (when (or (f/text? field) (f/categorical? field) (f/calculate? field))
        [:input {:type "text"
                 :placeholder "Type or click to filter:"
                 :on-key-up (fn [e] (filter! (.-value (.-target e))))}])))))

(defmethod viewby-answer-list :default
  [cursor owner]
  (om/component
   (let [{:keys [answer->color answer->count answer->selected?
                 answers field visible-answers]} cursor
         language (:current (om/observe owner (shared/language-cursor)))
         toggle! (fn [ans]
                   (om/transact! cursor :answer->selected?
                                 #(vb/toggle-answer-selected %
                                                             visible-answers
                                                             ans))
                   (put! shared/event-chan {:view-by-filtered true}))]
     (html
      [:ul
       (om/build viewby-answer-list-filter cursor)
       (for [answer visible-answers]
         (let [selected? (answer->selected? answer)
               col (answer->color answer)
               acount (or (answer->count answer) 0)
               answer-s (format-answer field answer language)]
           [:li
            [:a (when answer {:href "#"
                              :on-click (click-fn #(toggle! answer))})
             [:div
              [:div {:class "small-circle"
                     :style {:background-color (if selected? col vb/grey)}}]
              [:div (when-not selected? {:style {:color vb/grey}})
               (str answer-s " (" acount ")")]]]]))]))))


(defmethod map-viewby-answer-legend :default
  [cursor owner]
  (reify
    om/IRender
    (render [_]
      (let [language (:current (om/observe owner (shared/language-cursor)))
            {:keys [field]} cursor]
        (html
         [:div {:class "legend viewby top left"}
          (om/build map-viewby-answer-close nil)
          [:div {:class "pure-menu pure-menu-open"}
           [:h4 (get-label field language)]
           (om/build viewby-answer-list cursor)]])))))

(defmethod map-viewby-legend :default
  [{:keys [view-by dataset-info]} owner opts]
  "The view by menu + legend.
   Menu renders each field. On-click triggers :view-by event, data = field.
   Legend renders the answers, which are put into the :view-by cursor."
  (reify
    om/IRenderState
    (render-state [_ state]
       (html (if (empty? view-by)
               (om/build map-viewby-menu dataset-info {:opts opts :init-state state})
               (om/build map-viewby-answer-legend view-by {:init-state state}))))))

(defmethod map-record-legend :default
  [cursor owner opts]
  (reify
    om/IRender
    (render [_]
      (let [{:keys [marker prev-marker]} cursor]
        (mu/apply-click-style marker)
        (mu/apply-unclick-style prev-marker)
        (om/build submission-view cursor {:opts (merge opts
                                                       {:view :map})})))))

(defn- load-geojson-helper
  "Helper for map-and-markers component (see below); loads geojson onto map.
   If map doesn't exists in local-state, creates it and puts it there.
   geojson, feature-layer, id-marker-map in local state are also updated."
  [owner geojson]
  (let [leaflet-map (or (om/get-state owner :leaflet-map)
                        (mu/create-map (om/get-node owner)
                                       (om/get-shared owner :map-config)))
        {:keys [feature-layer id->marker]}
          (mu/load-geo-json leaflet-map geojson shared/event-chan :rezoom? true)]
    (om/set-state! owner :leaflet-map leaflet-map)
    (om/set-state! owner :feature-layer feature-layer)
    (om/set-state! owner :id-marker-map id->marker)
    (om/set-state! owner :geojson geojson)))

(defmethod map-and-markers :default [app-state owner]
  "Map and markers. Initializes leaflet map + adds geojson data to it.
   Cursor is at :map-page"
  (reify
    om/IRenderState
    (render-state [_ _]
      "render-state simply renders an emtpy div that leaflet will render into."
      (html [:div {:id "map"}]))
    om/IDidMount
    (did-mount [_]
      "did-mount loads geojson on map, and starts the event handling loop."
      (let [data (:data app-state)
            form (om/get-shared owner :flat-form)
            geojson (mu/as-geojson data form)
            rerender! #(mu/re-render-map! (om/get-state owner :leaflet-map)
                                          (om/get-state owner :feature-layer))]
        (load-geojson-helper owner geojson)
        (handle-map-events app-state
                           {:re-render! rerender!
                            :get-id-marker-map
                            #(om/get-state owner :id-marker-map)})))
    om/IWillReceiveProps
    (will-receive-props [_ next-props]
      "will-recieve-props resets leaflet geojson if the map data has changed."
      (let [old-data (:data (om/get-props owner))
            new-data (:data next-props)
            old-field (get-in (om/get-props owner) [:map-page :geofield])
            new-field (get-in next-props [:map-page :geofield])]
        (when (or (not= old-field new-field)
                  (not= (count old-data) (count new-data))
                  (not= old-data new-data))
          (let [{:keys [flat-form]} (om/get-shared owner)
                {:keys [leaflet-map feature-layer geojson]} (om/get-state owner)
                new-geojson (mu/as-geojson new-data flat-form new-field)]
            (when (not= geojson new-geojson)
              (when leaflet-map (.removeLayer leaflet-map feature-layer))
              (load-geojson-helper owner new-geojson)
              (put! shared/event-chan {:data-updated true}))))))))

(defmethod map-geofield-chooser :default
  [geofield owner {:keys [geofields]}]
  (reify
    om/IInitState
    (init-state [_]
      "Update geofield cursor if necessary, and return {:expanded false}"
      (when (empty? geofield)
        (om/update! geofield
                    (f/default-geofield (om/get-shared owner [:flat-form]))))
      {:expanded false})
    om/IRenderState
    (render-state [_ _]
      "Render component w/ css + expansion technique from leaflet layer control"
      (when (< 1 (count geofields))
        (let [with-suffix #(if-not (om/get-state owner :expanded) %
                             (str % " leaflet-control-layers-expanded"))]
          (html
           [:div.leaflet-left.leaflet-bottom {:style {:margin-bottom "105px"}}
            [:div {:class (with-suffix "leaflet-control leaflet-control-layers")
                   :on-mouse-enter #(om/set-state! owner :expanded true)
                   :on-mouse-leave #(om/set-state! owner :expanded false)}
             [:a.icon-map.field-chooser {:href "#" :title "Choose Geo Field"}]
             [:form {:class "leaflet-control-layers-list"}
              (for [field geofields]
                [:label
                 [:input.leaflet-control-layers-selector
                  {:type "radio" :checked (= field geofield)
                   :on-click (click-fn #(om/update! geofield field))}]
                 (get-label field) [:br]])]]]))))))

(defmethod map-page :default
  [cursor owner opts]
  "The overall map-page om component.
   Has map-and-markers, as well as the legend as child components.
   Contains channels in its state; these are passed onto all child components."
  (reify
    om/IRender
    (render [_]
      (let [form (om/get-shared owner [:flat-form])]
        (html
         [:div {:id "map-holder"}
          (om/build map-and-markers
                    cursor
                    {:opts opts})
          (om/build map-geofield-chooser
                    (get-in cursor [:map-page :geofield])
                    {:opts {:geofields (filter f/geofield? form)}})
          (om/build map-viewby-legend
                    {:view-by (get-in cursor [:map-page :view-by])
                     :dataset-info (get-in cursor [:dataset-info])}
                    {:opts opts})
          (om/build map-record-legend
                    (merge
                     {:geofield (get-in cursor [:map-page :geofield])}
                     (get-in cursor [:map-page :submission-clicked]))
                    {:opts opts})])))))
