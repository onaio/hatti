(ns hatti.ona.urls
  (:require [hatti.utils :refer [url format]]
            [hatti.shared :refer [app-state]]))

(defn media-url [id fname]
  (str (:api-url @app-state) "files" (url (format "%s?filename=%s" id fname))))
