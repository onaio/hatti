(ns hatti.ona.urls
  (:require [hatti.utils :refer [url format]]))

(def base-uri "http://ona.io/api/v1")

(defn data-url [endpoint dataset-id & {:keys [:format] :or {:format "json"}}]
  (url base-uri endpoint (str dataset-id "." format)))

(defn formjson-url [dataset-id]
  (url base-uri "forms" dataset-id "form.json"))

(defn chart-url [dataset-id field-name]
  (url base-uri "charts"
       (str dataset-id ".json?field_name=" field-name)))

(defn media-url [id fname]
  (url base-uri "files" (format "%s?filename=%s" id fname)))
