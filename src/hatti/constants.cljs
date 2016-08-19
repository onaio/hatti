(ns hatti.constants)

(def _id "_id")
(def _rank "_rank")
(def _submission_time "_submission_time")
(def _submitted_by "_submitted_by")

;; Google Sheets app-type.
(def google-sheets "google_sheets")

;; Map configs
(def tiles-server "http://localhost:3001")
(def tiles-endpoint
  "/services/postgis/logger_instance/geom/vector-tiles/{z}/{x}/{y}.pbf")
(def mapboxgl-access-token
  "pk.eyJ1Ijoib25hIiwiYSI6IlVYbkdyclkifQ.0Bz-QOOXZZK01dq4MuMImQ")
(def map-styles-url #(str "mapbox://styles/mapbox/" % "-v9"))
(def map-styles {"basic" "Mapbox Basic"
                  "outdoors" "Mapbox Outdoors"
                  "streets" "MapBox"
                  "bright" "Mapbox Bright"
                  "light" "Mapbox Light"
                  "dark" "Mapbox Dark"
                  "satellite-streets" "Mapbox Satellite"})
;; For leaflet
(def mapping-threshold 1000000)
