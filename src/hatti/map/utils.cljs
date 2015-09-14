(ns hatti.map.utils
  (:use [cljs.reader :only [read-string]])
  (:require [clojure.string :as string]
            [cljs.core.async :refer [put!]]
            [cljsjs.leaflet]
            [hatti.constants :refer [_id _rank]]
            [hatti.ona.forms :as f]
            [hatti.utils :refer [indexed]]))

;; STYLES

(def ona-styles
  {:point {:normal  #js {:radius 6
                         :fillColor "#f30"
                         :reset #js {:fillColor "#f30"}
                         :color "#fff"
                         :border 8
                         :opacity 0.5
                         :fillOpacity 0.9}
           :hover   #js {:fillColor "#631400"}
           :clicked #js {:fillColor "#ad2300"}}
   :shape {:normal  #js {:fillColor "#f30"
                         :color "#666"
                         :weight 3
                         :dashArray "3"
                         :fillOpacity 0.7
                         :opacity 0.8}
           :hover   #js {:fillColor "#631400"
                         :color "#222"}
           :clicked #js {:fillColor "#ad2300"
                         :color "#222"}}
   :line {:normal #js {:color "#f30"
                       :opacity 1
                       :weight 6
                       :reset #js {:color "#f30"}}
          :hover #js {:color "#631400"}
          :clicked #js {:color "#ad2300"}}})

(defn marker->geotype
  "Returns geotype (:point :line or :shape) based on marker."
  [marker]
  (-> marker
      (aget "feature") (aget "geometry") (aget "type")
      {"Point" :point "Polygon" :shape "LineString" :line}))

(defn get-ona-style
  "Appropriate style given style-type (:normal, :clicked, :hover), and
   either a leaflet marker or clojurescript keyword (one of :point or :shape)."
  [marker_or_keyword style-type]
  (let [kw (if (keyword? marker_or_keyword)
             marker_or_keyword
             (marker->geotype marker_or_keyword))]
    (-> ona-styles kw style-type)))

(defn equivalent-style [s t]
  "Checks that for all common properties, s and t are equivalent styles."
  (let [sc (js->clj s) tc (js->clj t)]
    (= (select-keys sc (keys tc)) (select-keys tc (keys sc)))))

;; MARKERS

(defn- get-id
  [marker]
  (-> marker (aget "feature") (aget "properties") (aget _id)))

(defn- get-rank
  [marker]
  (-> marker (aget "feature") (aget "properties") (aget _rank)))

(defn get-style
  "Get the style of a marker. Second arg specifies style attribute to get.
   eg. For marker m1, call like: (get-style m1) or (get-style m1 :fillColor)"
  ([marker] (aget marker "options"))
  ([marker kw] (kw (js->clj (get-style marker)))))

(defn- is-clicked?
  "Check whether a marker is clicked, so it's style can be preserved."
  [marker]
  (equivalent-style (get-style marker) (get-ona-style marker :clicked)))

(defn re-style-marker
  "Apply a style to a marker. Style comes from a function that takes marker."
  [marker->style marker]
  (let [style (marker->style marker)]
    (if (is-clicked? marker)
      ;; we only set reset style on clicked markers
      (.setStyle marker (clj->js {:reset style}))
      (.setStyle marker (clj->js (assoc style :reset style))))))

(defn reset-style
  "Reset styles pulls the 'reset' property from within a markers options,
   clearing styles to default if nothing found."
  [marker]
  (if-let [rstyle (aget (aget marker "options") "reset")]
    (.setStyle marker rstyle)
    (.setStyle marker (get-ona-style marker :normal))))

(defn- bring-to-top-if-selected
  "Looks at the _id in marker, and brings to top if (1) marker clicked or
   (2) marker's id is selected (via id-selected?)"
  [id-selected? marker]
  (when (or (is-clicked? marker) (id-selected? (get-id marker)))
    (.bringToFront marker)))

(defn apply-click-style [marker]
  (when marker
    (.bringToFront marker)
    (.setStyle marker (get-ona-style marker :clicked))))

(defn apply-unclick-style [marker]
  (when marker
    (reset-style marker)))

(defn apply-hover-style [marker]
  (when marker
    (.bringToFront marker)
    (.setStyle marker (get-ona-style marker :hover))))

;; MARKERS

(defn clear-all-styles
  "Sets the default style on a marker."
  [markers &{:keys [preserve-clicked?] :or {preserve-clicked? true}}]
  (doseq [marker markers]
    (if-not (and preserve-clicked? (is-clicked? marker))
      (.setStyle marker (get-ona-style marker :normal)))))


;; GEOJSON CONVERSION

(defn read-string-or-number
  [maybe-s]
  (if (string? maybe-s) (read-string maybe-s) maybe-s))

(defn- get-as-geom
  [record geofield]
  (let [geotype ({"geopoint" "Point"
                  "gps" "Point"
                  "geoshape" "Polygon"
                  "geotrace" "LineString"} (:type geofield))
        parse (fn [s] (when (and (seq s) (not= s "n/a"))
                       (for [coord-string (string/split s #";")]
                         (let [[lat lng _ _ ] (string/split coord-string #" ")]
                           [(read-string lng) (read-string lat)]))))
        coordfn (case geotype
                  "Point" #(first (parse %))
                  "LineString" parse
                  "Polygon" #(vector (parse %))
                  identity)
        key (:full-name geofield)
        value (get record key)
        coords (coordfn value)]
    (if (f/osm? geofield)
      (:geom value)
      (when-not (or (nil? coords) (some nil? coords))
        {:type geotype :coordinates coords}))))

(defn as-geojson
  "Given the dataset, and the form schema, get out geojson.
   Optional specification of field will map that field data to the geom."
  ([dataset form]
    (as-geojson dataset form (f/default-geofield form)))
  ([dataset form geofield]
     (when geofield
       {:type "FeatureCollection"
        :features (for [[idx record] (indexed dataset)
                        :let [geo (get-as-geom record geofield)]
                        :when geo]
                    {:type "Feature"
                     :properties {(keyword _rank) (inc idx)
                                  (keyword _id) (record _id)}
                     :geometry geo})})))

;;;;; MAP

(defn create-map
  "Creates a leaflet map, rendering it to the dom element with given id."
  [id {:keys [mapbox-tiles include-google-maps?]}]
  (let [layers (map #(.tileLayer js/L (:url %)) mapbox-tiles)
        nlayers (zipmap (map :name mapbox-tiles) layers)
        named-layers (if include-google-maps?
                       (clj->js (assoc nlayers
                                  "Google Satellite" (js* "new L.Google")))
                       (clj->js nlayers))
        m (.map js/L id #js {:layers (first layers) :zoomControl false})
        z ((.. js/L -control -zoom) #js {:position "bottomleft"})]
    ;; zoom control
    (.addTo z m)
    ;; layers control
    (-> ((js* "L.control.layers") named-layers nil #js {:position "bottomleft"})
        (.addTo m))
    (.setView m #js [0 0] 1)
    m))

(defn- register-mouse-events
  "Mouse events for markers.
  On click or arrow, submission-to-rank events are generated.
  On hover, marker is brought to the front and color changed."
  [feature marker event-chan]
  (.on marker "click"
       #(when-not (is-clicked? marker)
          (put! event-chan {:submission-to-rank
                            (aget (aget feature "properties") _rank)})))
  (.on marker "mouseover"
       #(when-not (is-clicked? marker)
          (apply-hover-style marker)))
  (.on marker "mouseout"
       #(when-not (is-clicked? marker)
          (apply-unclick-style marker))))

(defn- re-render-map! [leaflet-map feature-layer]
  "Re-renders map by invalidating leaflet size.
   If map is zoomed out beyond layer-bounds, re-zooms to layer."
  (let [map-bounds (.getBounds leaflet-map)
        layer-bounds (.getBounds feature-layer)]
    (.invalidateSize leaflet-map false)
    ;; Re-fitting to bounds should happen if the map is more zoomed out
    ;; than the data. The 0 get-zoom is an additional constraint for
    ;; datasets that cross the axes (contain both -ve, +ve lat,lngs)
    (when (or (zero? (.getZoom leaflet-map))
              (not (.contains layer-bounds map-bounds)))
      (.fitBounds leaflet-map layer-bounds))))

(defn- load-geo-json
  "Create a map with the given GeoJSON data.
   Adds mouse events and centers on the geojson features."
  [m geojson event-chan & {:keys [rezoom?]}]
  (let [on-events #(register-mouse-events %1 %2 event-chan)
        geometry-type (-> geojson :features first :geometry :type
                          {"Point" :point "Polygon" :shape "LineString" :line})
        stylefn #(get-ona-style geometry-type :normal)
        point->marker #(.circleMarker js/L %2)
        feature-layer (.geoJson js/L
                                (clj->js geojson)
                                #js {:onEachFeature on-events
                                     :pointToLayer point->marker
                                     :style stylefn})
        ids (map #(get-in % [:properties (keyword _id)]) (:features geojson))
        markers (.getLayers feature-layer)]
    (when-not (empty? (:features geojson))
      (when rezoom? (.fitBounds m (.getBounds feature-layer)))
      (.addTo feature-layer m))
    {:feature-layer feature-layer :markers markers :id->marker (zipmap ids markers)}))
