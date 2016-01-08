(ns hatti.ona.urls
  (:require [hatti.utils :refer [url format]]
            [hatti.shared :refer [app-state]]))

(defn media-url [id fname]
  (url (str (:api-url @app-state) "files") (format "%s?filename=%s" id fname)))
