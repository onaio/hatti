(ns hatti.ona.urls
  (:require [hatti.utils :refer [url format]]))

(def base-uri "//api.ona.io/api/v1")

(defn media-url [id fname]
  (url base-uri "files" (format "%s?filename=%s" id fname)))
