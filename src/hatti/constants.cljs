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
;; For leaflet
(def mapping-threshold 10000)
