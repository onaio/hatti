(ns hatti.constants)

(def _attachments "_attachments")
(def _id "_id")
(def _rank "_rank")
(def _submission_time "_submission_time")
(def _submitted_by "_submitted_by")
(def photo "Photo")

;; Attachments keys
(def download-url "download_url")
(def medium-download-url "medium_download_url")
(def small-download-url "small_download_url")
(def mimetype "mimetype")

;; Google Sheets app-type.
(def google-sheets "google_sheets")

;; Map configs
(def tiles-endpoint
  "/services/postgis/logger_instance/geom/vector-tiles/{z}/{x}/{y}.pbf")
(def mapboxgl-access-token
  "pk.eyJ1Ijoib25hIiwiYSI6IlVYbkdyclkifQ.0Bz-QOOXZZK01dq4MuMImQ")

(def map-styles {"basic" ["Mapbox Basic"]
                 "outdoors" ["Mapbox Outdoors"]
                 "streets" ["MapBox"]
                 "bright" ["Mapbox Bright"]
                 "light" ["Mapbox Light"]
                 "dark" ["Mapbox Dark"]
                 "satellite-streets" ["Mapbox Satellite"]})
(def mapping-threshold 10000)
