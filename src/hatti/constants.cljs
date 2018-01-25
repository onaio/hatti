(ns hatti.constants)

(def _attachments "_attachments")
(def _edited "_edited")
(def _id "_id")
(def _last_edited "_last_edited")
(def _notes "_notes")
(def _rank "_rank")
(def _submission_time "_submission_time")
(def _media_all_received "_media_all_received")
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
(def vector-source-layer "logger_instance_geom")
(def mapboxgl-access-token
  "pk.eyJ1Ijoib25hIiwiYSI6IlVYbkdyclkifQ.0Bz-QOOXZZK01dq4MuMImQ")

(def map-styles
  [{:style "basic" :name "Mapbox Basic"}
   {:style "outdoors" :name "Mapbox Outdoors"}
   {:style "streets" :name "Mapbox Streets"}
   {:style "bright" :name "Mapbox Bright"}
   {:style "light" :name "Mapbox Light"}
   {:style "dark" :name "Mapbox Dark"}
   {:style "satellite-streets" :name "Mapbox Satellite"}])
(def mapping-threshold 10000)

(def hexgrid-id "hexgrid")
(def hexbin-cell-width 60)
(def min-count-color "#f2f5fc")
(def max-count-color "#08519c")

(def failed-media-upload-error-message
  "This media file was not uploaded successfully.")

(def failed-media-upload-help-url
  "knowledge-base/media-files-metadata-field/")

(defn help-base-url
  "Define a help URL"
  [suffix]
  (str "https://help.ona.io/" suffix))
