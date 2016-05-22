(ns hatti.ona.urls
  (:require [chimera.js-interop :refer [format]]
            [chimera.urls :refer [url last-url-param]]
            [hatti.shared :refer [app-state]]))

(defn media-url [id fname]
  (str (:api-url @app-state) "files" (url (format "%s?filename=%s" id fname))))
