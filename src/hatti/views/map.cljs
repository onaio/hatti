(ns hatti.views.map
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :refer [<! chan put! timeout]]
            [clojure.string :as string]
            [chimera.js-interop :refer [json->cljs]]
            [chimera.seq :refer [in?]]
            [om.core :as om :include-macros true]
            [sablono.core :as html :refer-macros [html]]
            [hatti.constants :refer [_id _rank
                                     hexbin-cell-width
                                     hexgrid-id
                                     map-styles
                                     mapboxgl-access-token
                                     mapping-threshold]]
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
                             (-> full-name chart-get <! :body))
            label->answer #(if (or (f/select-one? field) (f/select-all? field))
                             (get
                              (apply merge (map (fn [{:keys [label name]}]
                                                  {label name}) children)) %)
                             %)
            get-label #(if (or (f/select-one? field)
                               (f/select-all? field)
                               (f/text? field))
                         (first %) %)
            field-key (keyword name)
            answers (for [d data]
                      (let [label (-> d field-key get-label)
                            answer (label->answer label)]
                        (str "{\"" full-name \" ":\"" answer "\"}")))
            query (str "{\"$or\":[" (string/join "," answers) "]}")
            fields (str "[\"" _id \"  ", \"" full-name "\"]")
            data (->> (<! (data-get nil {:query query :fields fields}))
                      :body json->cljs (remove empty?))]
        (om/update! app-state [:map-page :view-by-data] data))))

(defn get-style-url
  "Returns selected map style URL."
  [style url & [access-token]]
  (set! (.-accessToken js/mapboxgl) (or access-token mapboxgl-access-token))
  (or url (str "mapbox://styles/mapbox/" style "-v9")))

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
              {:keys [dataset-info]} @app-state
              field (:field view-by)
              data-not-in-appstate? (> (:num_of_submissions dataset-info)
                                       mapping-threshold)]

          ;; Fetches data to generated view-by data if not all data is in
          ;; app-state
          (when (and data-not-in-appstate? view-by)
            (om/update! app-state [:map-page :view-by]
                        {:field field :loading? true})
            (<! (get-viewby-data app-state opts field)))
          (when view-by
            (let [data (if data-not-in-appstate?
                         (-> @app-state :map-page :view-by-data)
                         (-> @app-state :map-page :data))
                  vb-info (vb/viewby-data field data)]
              (om/update! app-state [:map-page :view-by] vb-info)
              (vb/apply-view-by! vb-info owner)))
          (when view-by-filtered
            (vb/apply-view-by!
             (get-in @app-state [:map-page :view-by]) owner))
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
                              (get-in @app-state [:map-page :data])))]
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
                             (get-in @app-state [:map-page :data])))]
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

(defn handle-map-events
  "Creates multiple channels and delegates events to them."
  [app-state opts]
  (handle-viewby-events app-state opts)
  (handle-submission-events app-state opts)
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
                 answer->selected? field visible-answers]}
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
               answer-string (format-answer field answer language)
               title (str answer-string " - " answer-count)]
           [:li
            [:a (when answer {:href "#"
                              :title title
                              :on-click (click-fn
                                         #(toggle! answer))})
             [:div
              [:div {:class "small-circle"
                     :style {:background-color (if selected? col grey)}}]
              [:div (when-not selected? {:style {:color grey}})
               (str answer-string " [" answer-count "]")]]]]))]))))

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

(defn- load-mapboxgl-helper
  "Helper for map-and-markers component (see below);
   If map doesn't exists in local-state, creates it and puts it there."
  [{:keys [dataset-info] {:keys [tiles-server]} :map-page :as app-state}
   owner & {:keys [geojson geofield]}]
  (let [mapboxgl-map (or (om/get-state owner :mapboxgl-map)
                         (mu/create-mapboxgl-map (om/get-node owner)))
        {:keys [formid id_string query]} dataset-info
        {:keys [flat-form]} (om/get-shared owner)

        ;; Only use tiles server endpoint as source loading a large dataset,
        ;;otherwise genereated geojson will be rendered on map.
        tiles-endpoint (when (and (> (:num_of_submissions dataset-info)
                                     mapping-threshold)
                                  tiles-server)
                         (mu/get-tiles-endpoint tiles-server
                                                formid ["id"] flat-form query))
        load-layers (fn []
                      (mu/map-on-load
                       mapboxgl-map shared/event-chan id_string
                       :tiles-url tiles-endpoint
                       :geojson geojson
                       :geofield geofield
                       :owner owner)
                      (om/set-state! owner :zoomed? false))
        fitBounds (fn [geojson]
                    (when (and (.loaded mapboxgl-map)
                               (not (om/get-state owner :zoomed?)))
                      (if (and (om/get-state owner :zoom)
                               (om/get-state owner :loaded?))
                        (.on mapboxgl-map "zoom" #(mu/set-zoom-level owner))
                        (mu/fitMapBounds
                         mapboxgl-map (:id_string dataset-info) geojson))
                      (om/set-state! owner :zoomed? true)))]
    ;; Handle Map Events
    (.on mapboxgl-map "style.load" load-layers)
    (.on mapboxgl-map "render" #(fitBounds
                                 (om/get-state owner [:geojson])))
    (.on mapboxgl-map "zoom" #(mu/set-zoom-level owner))

    ;; Handles change in geojson source when geofield is changed
    (when (and geojson (-> geojson :features count pos?))
      (if (or (.loaded mapboxgl-map) (om/get-state owner [:loaded?]))
        (do
          (.off mapboxgl-map "style.load")
          (.off mapboxgl-map "load")
          (.on mapboxgl-map "style.load" load-layers)
          (load-layers))
        (do
          (.off mapboxgl-map "load")
          (.on mapboxgl-map "load" load-layers)))
      (om/set-state! owner :geojson geojson))
    (om/set-state! owner :mapboxgl-map mapboxgl-map)
    (om/set-state! owner :layer-id id_string)
    (om/update! app-state [:map-page :mapboxgl-map] mapboxgl-map)))

(defn mapboxgl-map
  "Map and markers. Initializes mapboxgl map + adds vector tile data to it.
   Cursor is at :map-page"
  [app-state owner opts]
  (reify
    om/IRenderState
    (render-state [_ _]
      "render-state simply renders an emtpy div that mapboxgl will render
      into."
      (html [:div#map]))
    om/IDidMount
    (did-mount [_]
      "did-mount loads geojson on map, and starts the event handling loop."
      (load-mapboxgl-helper app-state owner)
      (handle-map-events
       app-state
       (merge
        (select-keys opts [:chart-get :data-get])
        {:owner owner
         :get-id-marker-map  #(om/get-state owner :id-marker-map)})))
    om/IWillReceiveProps
    (will-receive-props [_ next-props]
      "will-recieve-props resets mapboxglmap
      switches to geojson source if the map data has changed to geoshapes."
      (let [{{old-map-data :data
              old-field :geofield
              {old-cell-width :cell-width} :hexbins
              old-viewby :view-by} :map-page} (om/get-props owner)
            {{new-map-data :data
              new-field :geofield
              {show-hexbins? :show?
               new-cell-width :cell-width
               hide-points? :hide-points?} :hexbins
              {show-heatmap? :show?} :heatmap
              new-viewby :view-by} :map-page} next-props
            {:keys [mapboxgl-map layer-id geojson]} (om/get-state owner)
            {:keys [flat-form]} (om/get-shared owner)
            data-changed? (or (not= old-field new-field)
                              (not= (count old-map-data) (count new-map-data))
                              (not= old-map-data old-map-data))
            new-geojson (if data-changed?
                          (mu/as-geojson new-map-data flat-form new-field)
                          geojson)
            view-by-changed? (and (not= old-viewby new-viewby)
                                  (not (nil? old-viewby)))
            cell-width-changed? (not= old-cell-width new-cell-width)
            opts (when (and show-hexbins? view-by-changed?)
                   (vb/get-selected-ids new-viewby))
            layer-opts (assoc opts
                              :cell-width new-cell-width
                              :hide-points? hide-points?)]
        ;; Update layers if data changes
        (when (and data-changed? (not-empty new-field)
                   (not= geojson new-geojson))
          (mu/remove-layer mapboxgl-map layer-id)
          (load-mapboxgl-helper app-state owner
                                :geojson new-geojson
                                :geofield new-field)
          (put! shared/event-chan {:data-updated true}))
        ;; update map state with layer options
        (om/set-state! owner :layer-opts layer-opts)
        ;; Render heatmap layer when show? :heatmap is toggled.
        (if show-heatmap?
          (mu/show-heatmap owner mapboxgl-map layer-id new-geojson layer-opts)
          (do
            (when (.loaded mapboxgl-map)
              (mu/show-hide-points mapboxgl-map layer-id))
            (mu/remove-layer mapboxgl-map "heatmap")
            (doseq [i (range 5)]
              (mu/remove-layer mapboxgl-map (str "cluster-" i)))
            (om/set-state! owner :show-heatmap? false)))
        ;; Render hexbins layer when show? :hexbin is toggled.
        (if show-hexbins?
          (mu/show-hexbins owner mapboxgl-map
                           layer-id new-geojson layer-opts)
          (do
            (when (.loaded mapboxgl-map)
              (mu/show-hide-points mapboxgl-map layer-id))
            (mu/remove-layer mapboxgl-map hexgrid-id)
            (om/set-state! owner :show-hexbins? false)))
        ;; Re-render hexbins when cell-width or view-by are changed.
        (when (and show-hexbins?
                   (or cell-width-changed? view-by-changed?))
          (mu/remove-layer mapboxgl-map hexgrid-id)
          (mu/show-hexbins owner mapboxgl-map layer-id
                           new-geojson layer-opts))))))

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
           [:div.leaflet-left.leaflet-bottom.geofield-selector
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

(defn map-hexbin-selector
  [{{{show-hexbins? :show?} :hexbins
     {show-heatmap? :show?} :heatmap} :map-page :as cursor} owner]
  (reify
    om/IRenderState
    (render-state [_ _]
      "Render hexbin selector component w/css + expansion technique from
      leaflet layer control."
      (html
       [:div.leaflet-left.leaflet-bottom.hexbin-selector
        [:div.leaflet-control.leaflet-control-layers
         [:a
          {:title "Hexbins Layer"
           :class (str "layer-toggle hexbin" (when show-hexbins? " active"))
           :on-click
           (click-fn
            #(om/update! cursor
                         [:map-page :hexbins :show?]
                         (not show-hexbins?)))}]
         [:a
          {:title "Heatmap Layer"
           :class (str "layer-toggle heatmap" (when show-heatmap? " active"))
           :on-click
           (click-fn
            #(om/update! cursor
                         [:map-page :heatmap :show?]
                         (not show-heatmap?)))}]]]))))

(defn map-hexbin-slider
  [{{{:keys [show? cell-width hide-points?]} :hexbins} :map-page :as cursor}
   owner]
  (reify
    om/IInitState
    (init-state [_]
      {:slider-value 0
       :zoom->bin? true
       :value->size {0 500 1 250 2 100 3 50 4 25 5 10 6 5 7 1 8 0.5}
       :max-bin 6})
    om/IWillReceiveProps
    (will-receive-props [_ next-props]
      (let [map (-> next-props :map-page :mapboxgl-map)
            zoom (when map (.getZoom map))
            value (condp #(<= %2 %1) zoom
                    -1 nil
                    2  0
                    5  1
                    8  2
                    11 3
                    14 4
                    17 5
                    20 6
                    nil)]
        (when (om/get-state owner :zoom->bin?)
          (om/set-state! owner :slider-value value))
      ;; allow 1km and 500m binning on zoom levels above 10
        (if (> zoom 10)
          (om/set-state! owner :max-bin 8)
          (om/set-state! owner :max-bin 6))))
    om/IWillUpdate
    (will-update [_ _ {:keys [slider-value value->size]}]
      (om/update! cursor [:map-page :hexbins :cell-width]
                  (get value->size slider-value)))
    om/IRenderState
    (render-state [_ {:keys [slider-value value->size max-bin]}]
      "Render layer selector component w/css + expansion technique from
      leaflet layer control."
      (html
       (when show?
         (let [cell-size (or cell-width (get value->size slider-value))
               update-cell-width (fn [e]
                                   (let
                                    [v (js/parseInt (.. e -target -value))]
                                     (om/update-state!
                                      owner #(assoc % :zoom->bin? false
                                                    :slider-value v))))
               below-1km? (> 1 cell-size)]
           [:div.map-overlay
            [:div.map-overlay-inner
             [:label.slider "Cell Width: "
              [:span#slider-value
               (str (cond-> cell-size below-1km? (* 1000))
                    (if below-1km? " Meters" " km"))]]
             [:input.slider#slider
              {:type "range" :min "0" :max (str max-bin) :step "1"
               :value slider-value
               :on-change update-cell-width}]
             [:input.show-points#show-points
              {:type "checkbox"
               :checked (when-not hide-points? "checked")
               :on-change #(om/update!
                            cursor
                            [:map-page :hexbins :hide-points?]
                            (not hide-points?))}]
             [:label {:for "show-points"} "Show points"]]]))))))

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
            mapboxgl-map (get-in cursor [:map-page :mapboxgl-map])
            custom-styles (om/get-shared owner
                                         [:map-config :custom-styles])]
        (html
         [:div.leaflet-left.leaflet-bottom.layer-selector
          [:div {:class (with-suffix "leaflet-control leaflet-control-layers")
                 :aria-haspopup "true"}
           [:a.leaflet-control-layers-toggle
            {:title "Layers"
             :on-mouse-enter #(om/set-state! owner :expanded true)}]
           [:form.leaflet-control-layers-list
            [:div.leaflet-control-layers-base
             {:on-mouse-leave #(om/set-state! owner :expanded false)}
             (for [{:keys [name style url access-token]}
                   (concat styles custom-styles)
                   :let [style (or style name)]]
               [:label
                [:input.leaflet-control-layers-selector
                 {:type "radio"
                  :name "leaflet-base-layers"
                  :on-click
                  (fn []
                    (om/set-state! owner :current-style style)
                    (.setStyle mapboxgl-map (get-style-url style
                                                           url
                                                           access-token))
                    (om/update! cursor [:map-page :style] style))
                  :checked (= style current-style)}]
                [:span " " name]])]
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
         [:div#map-holder
          (om/build mapboxgl-map
                    cursor
                    {:opts opts})
          (om/build map-geofield-chooser
                    (get-in cursor [:map-page :geofield])
                    {:opts {:geofields (filter f/geofield? form)}})
          (om/build map-hexbin-selector
                    cursor
                    {:opts opts})
          (om/build map-hexbin-slider
                    cursor
                    {:opts opts})
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
