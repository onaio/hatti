(ns hatti.views.map
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :refer [<! chan put! timeout]]
            [clojure.string :as string]
            [chimera.js-interop :refer [json->cljs]]
            [om.core :as om :include-macros true]
            [sablono.core :as html :refer-macros [html]]
            [hatti.constants :as constants :refer [_id _rank
                                                   map-styles-url
                                                   map-styles]]
            [hatti.ona.forms :as f :refer [format-answer get-label get-icon]]
            [hatti.utils :refer [click-fn]]
            [hatti.utils.style :refer [grey]]
            [hatti.map.utils :as mu]
            [hatti.map.viewby :as vb]
            [hatti.shared :as shared]
            [hatti.views :as views
             :refer [map-page map-and-markers map-geofield-chooser
                     map-record-legend submission-view
                     map-viewby-legend map-viewby-menu
                     viewby-answer-list viewby-answer-list-filter
                     map-viewby-answer-legend map-viewby-answer-close]]
            [hatti.views.record]))

;;;; HELPERS
(defn get-viewby-data
  "Gets view-by data from charts and data endpoints if the data is not in
  app-state"
  [app-state {:keys [chart-get data-get]}
   {:keys [name full-name children] :as field}]
  (go (let [{:keys [data]} (when name
                             (-> name chart-get <! :body))
            label->answer #(if (or (f/select-one? field) (f/select-all? field))
                             (get
                              (apply merge (map (fn [{:keys [label name]}]
                                                  {label name}) children)) %)
                             %)
            field-key (keyword name)
            answers (for [d data]
                      (let [label (-> d field-key first)
                            answer (label->answer label)]
                        (str "{\"" full-name \" ":\"" answer "\"}")))
            query (str "{\"$or\":[" (string/join "," answers) "]}")
            fields (str "[\"" _id \"  ", \"" full-name "\"]")
            data (->> (<! (data-get nil {:query query :fields fields}))
                      :body json->cljs (remove empty?))]
        (om/update! app-state [:map-page :data] [])
        (om/transact! app-state [:map-page :data] #(concat % data)))))

;;;;; EVENT HANDLERS

(defn handle-viewby-events
  "Listens to view-by events, and change the map-cursor appropriately.
   Needs access to app-state, event channels, as well as map objects."
  [app-state {:keys [get-id-marker-map owner chart-get] :as opts}]
  (let [event-chan (shared/event-tap)]
    (go
      (while true
        (let [e (<! event-chan)
              {:keys [view-by view-by-closed view-by-filtered]} e
              markers (vals (get-id-marker-map))
              {:keys [data dataset-info]} @app-state
              field (:field view-by)
              data-not-in-appstate? (< (count data)
                                       (:num_of_submissions dataset-info))]

          ;; Fetches view-by data if data not in app-state
          (when (and data-not-in-appstate? view-by)
            (om/update! app-state [:map-page :view-by]
                        {:field field :loading? true})
            (<! (get-viewby-data app-state opts field)))
          (when view-by
            (let [data (if data-not-in-appstate?
                         (-> @app-state :map-page :data)
                         (:data @app-state))
                  vb-info (vb/viewby-info field data false)]
              (om/update! app-state [:map-page :view-by] vb-info)
              (vb/view-by! vb-info markers owner)))
          (when view-by-filtered
            (vb/view-by! (get-in @app-state [:map-page :view-by]) markers
                         owner))
          (when view-by-closed
            (om/update! app-state [:map-page :view-by] {})
            (mu/clear-all-styles markers)
            (mu/clear-map-styles owner)))))))

(defn handle-submission-events
  "Listens to sumission events, and change the map-cursor appropriately.
   Needs access to app-state, event channels, as well as map objects."
  [app-state {:keys [get-id-marker-map data-get]}]
  (let [event-chan (shared/event-tap)]
    (go
      (while true
        (let [e (<! event-chan)
              {:keys [mapped-submission-to-rank submission-unclicked
                      mapped-submission-to-id]} e
              prev-marker (get-in @app-state
                                  [:map-page :submission-clicked :marker])]
          (when submission-unclicked
            (om/update! app-state [:map-page :submission-clicked]
                        {:data nil :prev-marker prev-marker}))
          (when mapped-submission-to-rank
            (let [rank mapped-submission-to-rank
                  new-data  (first
                             (filter
                              #(= rank (get % _rank))
                              (get-in @app-state [:data])))]
              (om/update! app-state [:map-page :submission-clicked]
                          {:data new-data
                           :marker (get (get-id-marker-map)
                                        (get new-data _id))
                           :prev-marker prev-marker})))
          (when mapped-submission-to-id
            (let [id mapped-submission-to-id
                  new-data (first
                            (filter
                             #(= id (get % _id))
                             (get-in @app-state [:data])))]
              (om/update! app-state [:map-page :submission-clicked]
                          {:data new-data
                           :id id
                           :marker (get (get-id-marker-map)
                                        (get new-data _id))
                           :prev-marker prev-marker})
              (when-not new-data
                (let [datum (-> id data-get <! :body json->cljs)]
                  (om/update! app-state [:map-page :submission-clicked]
                              {:data datum
                               :id id
                               :marker (get (get-id-marker-map)
                                            (get datum _id))
                               :prev-marker prev-marker}))))))))))

(defn handle-data-updates
  "Fires events that need to be re-fired when data updates."
  [app-state]
  (let [event-chan (shared/event-tap)]
    (go
      (while true
        (let [{:keys [data-updated] :as e} (<! event-chan)]
          (when data-updated
            (put! shared/event-chan
                  {:mapped-submission-to-rank
                   (get-in @app-state
                           [:map-page :submission-clicked :data _rank])})
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
                         (vb/filter-answer-data-structures
                          answers query field language)]
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
   (let [{:keys [answer->color answer->count answer->count-with-geolocations
                 answer->selected? answers field visible-answers]}
         cursor
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
               answer-count (or (answer->count answer) 0)
               answer-count-with-geolocations
               (or (answer->count-with-geolocations answer) 0)
               answer-string (format-answer field answer language)
               title (str answer-count-with-geolocations " of "
                          answer-count " submissions have geo data")
               disabled? (<= answer-count-with-geolocations
                             0)]
           [:li
            [:a (when answer {:href "#"
                              :title title
                              :class (when disabled? "is-disabled")
                              :on-click (click-fn
                                         #(when-not disabled?
                                            (toggle! answer)))})
             [:div
              [:div {:class "small-circle"
                     :style {:background-color (if selected? col grey)}}]
              [:div (when-not selected? {:style {:color grey}})
               (str answer-string " ["
                    answer-count-with-geolocations "/"
                    answer-count "]")]]]]))]))))

(defmethod map-viewby-answer-legend :default
  [cursor owner]
  (reify
    om/IRender
    (render [_]
      (let [language (:current (om/observe owner (shared/language-cursor)))
            {:keys [field loading?]} cursor]
        (html
         [:div {:class "legend viewby top left"}
          (om/build map-viewby-answer-close nil)
          [:div {:class "pure-menu pure-menu-open"}
           [:h4 (get-label field language)]
           (if loading?
             [:span [:i.fa.fa-spinner.fa-pulse] "Loading filters ..."]
             (om/build viewby-answer-list cursor))]])))))

(defmethod map-viewby-legend :default
  [{:keys [view-by dataset-info]} owner opts]
  "The view by menu + legend.
   Menu renders each field. On-click triggers :view-by event, data = field.
   Legend renders the answers, which are put into the :view-by cursor."
  (reify
    om/IRenderState
    (render-state [_ state]
      (html (if (empty? view-by)
              (om/build map-viewby-menu
                        dataset-info {:opts opts :init-state state})
              (om/build map-viewby-answer-legend
                        view-by {:init-state state}))))))

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
      (let [{{:keys [data]} :map-page} app-state
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
      (let [{{old-data :data} :map-page} (om/get-props owner)
            {{new-data :data} :map-page} next-props
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

(defn- load-mapboxgl-helper
  "Helper for map-and-markers component (see below);
   If map doesn't exists in local-state, creates it and puts it there."
  [{:keys [dataset-info tiles-server] :as app-state} owner
   & {:keys [geojson geofield]}]
  (let [mapboxgl-map (or (om/get-state owner :mapboxgl-map)
                         (mu/create-mapboxgl-map (om/get-node owner)))
        {:keys [formid id_string]} dataset-info
        tiles-endpoint (mu/get-tiles-endpoint
                        (or tiles-server constants/tiles-server) formid ["id"])
        load-layers (fn []
                      (mu/map-on-load
                       mapboxgl-map shared/event-chan id_string
                       :tiles-url tiles-endpoint :geojson geojson
                       :geofield geofield :owner owner)
                      (when (empty? geojson)
                        (om/set-state! owner :zoomed? false)))
        fitBounds (fn []
                    (when (and (.loaded mapboxgl-map)
                               (not (om/get-state owner :zoomed?)))
                      (mu/fitMapBounds mapboxgl-map (:id_string dataset-info))
                      (om/set-state! owner :zoomed? true)))]
    (.on mapboxgl-map "load" load-layers)
    (.on mapboxgl-map "style.load" load-layers)
    (.on mapboxgl-map "render" fitBounds)
    (when geojson (load-layers) (om/set-state! owner :geojson geojson))
    (om/set-state! owner :mapboxgl-map mapboxgl-map)
    (om/set-state! owner :layer-id id_string)
    (om/update! app-state [:map-page :mapboxgl-map] mapboxgl-map)))

(defn mapboxgl-map
  [app-state owner opts]
  "Map and markers. Initializes mapboxgl map + adds vector tile data to it.
   Cursor is at :map-page"
  (reify
    om/IRenderState
    (render-state [_ _]
      "render-state simply renders an emtpy div that mapboxgl will render
      into."
      (html [:div#map]))
    om/IDidMount
    (did-mount [_]
      "did-mount loads geojson on map, and starts the event handling loop."
      (let [re-render! #(identity "")]
        (load-mapboxgl-helper app-state owner)
        (handle-map-events
         app-state
         (merge
          (select-keys opts [:chart-get :data-get])
          {:owner owner
           :re-render! re-render!
           :get-id-marker-map  #(om/get-state owner :id-marker-map)}))))
    om/IWillReceiveProps
    (will-receive-props [_ next-props]
      "will-recieve-props resets mapboxglmap
      swiches to geojson source if the map data has changed to geoshapes."
      (let [{old-data :data} (om/get-props owner)
            {new-data :data} next-props
            old-field (get-in (om/get-props owner) [:map-page :geofield])
            new-field (get-in next-props [:map-page :geofield])]
        (when (or (not= old-field new-field)
                  (not= (count old-data) (count new-data))
                  (not= old-data new-data))
          (let [{:keys [flat-form]} (om/get-shared owner)
                {:keys [mapboxgl-map layer-id geojson]}
                (om/get-state owner)
                new-geojson (mu/as-geojson new-data flat-form new-field)]
            (when (and (not-empty old-field) (not= geojson new-geojson))
              (when (.getLayer mapboxgl-map layer-id)
                (.removeLayer mapboxgl-map layer-id)
                (.removeSource mapboxgl-map layer-id)
                (load-mapboxgl-helper app-state owner
                                      :geojson new-geojson
                                      :geofield new-field)
                (put! shared/event-chan {:data-updated true})))))))))

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
           [:div.leaflet-left.leaflet-bottom {:style {:margin-bottom "148px"}}
            [:div {:class (with-suffix "leaflet-control leaflet-control-layers")
                   :on-mouse-enter #(om/set-state! owner :expanded true)
                   :on-mouse-leave #(om/set-state! owner :expanded false)}
             [:a.icon-map.field-chooser {:href "#" :title "Choose Geo Field"}]
             [:form.leaflet-control-layers-list
              (for [field geofields]
                [:label
                 [:input.leaflet-control-layers-selector
                  {:type "radio" :checked (= field geofield)
                   :on-click (click-fn #(om/update! geofield field))}]
                 (get-label field) [:br]])]]]))))))

(defn map-layer-selector
  [cursor owner]
  (reify
    om/IInitState
    (init-state [_]
      {:expanded false
       :styles map-styles
       :current-style "streets"})
    om/IRenderState
    (render-state [_ {:keys [expanded styles current-style]}]
      "Render layer selector component w/css + expansion technique from
      leaflet layer control."
      (let [with-suffix
            #(if-not expanded % (str % " leaflet-control-layers-expanded"))
            map (get-in cursor [:map-page :mapboxgl-map])]
        (html
         [:div.leaflet-left.leaflet-bottom {:style {:margin-bottom "105px"}}
          [:div {:class (with-suffix "leaflet-control leaflet-control-layers")
                 :aria-haspopup "true"}
           [:a.leaflet-control-layers-toggle
            {:title "Layers"
             :on-mouse-enter #(om/set-state! owner :expanded true)}]
           [:form.leaflet-control-layers-list
            [:div.leaflet-control-layers-base
             {:on-mouse-leave #(om/set-state! owner :expanded false)}
             (for [[k v] styles]
               [:label
                [:input.leaflet-control-layers-selector
                 {:type "radio"
                  :name "leaflet-base-layers"
                  :on-click
                  (fn []
                    (om/set-state! owner :current-style k)
                    (.setStyle
                     map (map-styles-url k))
                    (put! shared/event-chan {:re-render :map}))
                  :checked (= k current-style)}]
                [:span " " v]])]
            [:div.leaflet-control-layers-separator {:style {:display "none"}}]
            [:div.leaflet-control-layers-overlays]]]])))))

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
          (om/build mapboxgl-map
                    cursor
                    {:opts opts})
          (om/build map-geofield-chooser
                    (get-in cursor [:map-page :geofield])
                    {:opts {:geofields (filter f/geofield? form)}})
          (om/build map-layer-selector
                    cursor
                    {:opts opts})
          (om/build map-viewby-legend
                    {:view-by (get-in cursor [:map-page :view-by])
                     :dataset-info (get-in cursor [:dataset-info])}
                    {:opts opts})
          (om/build map-record-legend
                    (merge
                     {:geofield (get-in cursor [:map-page :geofield])}
                     (get-in cursor [:map-page :submission-clicked]))
                    {:opts opts})])))))
