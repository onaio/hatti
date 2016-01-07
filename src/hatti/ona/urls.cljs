(ns hatti.ona.urls
  (:require [hatti.utils :refer [url format]]
            [hatti.shared :refer [app-state]]))

(def base-uri (atom "//api.ona.io/api/v1/"))

(defn media-url [id fname]
  (url (str @base-uri "files") (format "%s?filename=%s" id fname)))
