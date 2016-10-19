(ns hatti.ona.post-process
  (:require [chimera.js-interop :refer [format]]
            [chimera.om.state :refer [transact!]]
            [chimera.urls :refer [url last-url-param]]
            [clojure.string :refer [join split]]
            [hatti.constants :refer [_attachments _id _rank]]
            [hatti.ona.forms :as forms]
            [hatti.ona.urls :as ona-urls]
            [cljsjs.jquery]
            [osmtogeojson]))

;; OSM POST-PROCESSING

(defn ona-osm-link
  "Given some data in Ona format, builds a data structure that we will use
   to link osm data to Ona data."
  [data form]
  (let [osmfields (filter forms/osm? form)]
    (->> (for [datum data]
           (for [field osmfields]
             (let [osmkey (:full-name field)
                   osmdatum (get datum osmkey)
                   osmid (and osmdatum (re-find #"[0-9]+" osmdatum))]
               (when osmid
                 {osmid (merge {:field field}
                               (select-keys datum #{_id _rank}))}))))
         flatten
         (into {}))))

(defn osm-xml->geojson
  "Takes OSM XML in string form, and returns cljs geojson."
  [osm-xml-string]
  (js->clj (js/osmtogeojson (.parseXML js/jQuery osm-xml-string))
           :keywordize-keys true))

(defn osm-id->osm-data
  "Given some data in OSM format, an Ona Form, and osm xml string,
   return a map from OSM ID to each osm feature. The map contains:
   :osm-id, :name, :tags from osm xml,
   :type, :geom from osm feature's geojson equivalent."
  [data form osm-xml]
  (let [ona-osm-link (ona-osm-link data form)
        osmgeo (osm-xml->geojson osm-xml)
        featureset (osmgeo :features)]
    (into {}
          (for [{:keys [type properties geometry] :as feature} featureset]
            (let [{:keys [type id tags]} properties]
              {id {:osm-id id
                   :type type
                   :geom geometry
                   :name (:name tags)
                   :tags tags}})))))

(defn integrate-osm-data!
  "Given some data post-processed from the ona server
   (ie, containing _id, _rank), and a string of osm-xml, produce a version with
   relevant osm data injected in."
  [app-state form osm-xml app-state-keys]
  (let [osm-fields (filter forms/osm? form)]
    (when (seq osm-fields)
      (let [data (get-in app-state app-state-keys)
            osm-data (osm-id->osm-data data form osm-xml)
            osm-val->osm-id #(re-find #"[-]?[0-9]+" %)
            osm-val->osm-data (fn [osm-val osm-id]
                                ;; The OSM-val can be either a string or a
                                ;; precomputed osm-data value. This condition
                                ;; ensures only strings are parsed for OSM ids
                                (if (string? osm-val)
                                  (if-let [osm-submission-data
                                           (osm-data osm-id)]
                                    osm-submission-data
                                    osm-val)
                                  osm-val))
            updater (fn [osm-key]
                      (fn [data]
                        (for [datum data]
                          (let [osm-id (or
                                        (get-in datum [(str osm-key
                                                            ":way:id")])
                                        (get-in datum [(str osm-key
                                                            ":node:id")]))]
                            (update-in datum [osm-key]
                                       #(osm-val->osm-data % osm-id))))))]

        (doseq [osm-field osm-fields]
          (transact! app-state app-state-keys
                     (updater (:full-name osm-field))))))))

;; IMAGE POST-PROCESSING

(defn url-obj
  "Calculate full image and thumbnail urls given attachment information."
  [media-obj]
  (let [media-id (get media-obj "id")
        fname (get media-obj "filename")
        file-url (ona-urls/media-url media-id fname)]
    {:filename fname
     :download_url file-url
     :small_download_url (str file-url "&suffix=small")}))

(defn get-attach-map
  "Helper function for integrate attachments; returns a function from
   a filename to a `url-obj` (see specs in `url-obj` function)."
  [record attachments]
  (let [attachments (or attachments (get record _attachments))
        fnames (map #(last-url-param (get % "filename")) attachments)
        fname->urlobj (zipmap fnames (map url-obj attachments))]
    ;; If urlobj isn't found, we'll just return filename
    (fn [fname] (get fname->urlobj fname fname))))

(defn integrate-attachments
  "Inlines media data from within _attachments into each record."
  [flat-form data & {:keys [attachments]}]
  (let [image-fields (filter forms/image? flat-form)]
    (for [record data]
      (let [attach-map (get-attach-map record attachments)]
        (reduce (fn [record img-field]
                  (update-in record [(:full-name img-field)] attach-map))
                record
                image-fields)))))

(defn integrate-attachments-in-repeats
  "Inlines data from within _attachments into each datapoint within repeats."
  [flat-form data]
  (let [repeat-fields (filter forms/repeat? flat-form)
        integrate (fn [record rpt-field]
                    (let [key (:full-name rpt-field)]
                      (assoc record key
                             (integrate-attachments
                              (:children rpt-field)
                              (get record key)
                              :attachments
                              (get record _attachments)))))]
    (for [record data]
      (reduce integrate record repeat-fields))))

(defn integrate-attachments!
  "Inlines data from within _attachments into each record within app-state."
  [app-state flat-form & {:keys [app-data-keys]
                          :or {app-data-keys [:data]}}]
  (transact! app-state
             app-data-keys
             #(->> %
                   (integrate-attachments flat-form)
                   (integrate-attachments-in-repeats flat-form))))
