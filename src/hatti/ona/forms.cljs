(ns hatti.ona.forms
  (:require [clojure.string :as string]
            [hatti.constants :refer [_submission_time _submitted_by]]
            [hatti.utils :refer [format last-url-param]]))

;; CONSTANTS
(def no-answer "No Answer")
(def submission-time-field
  {:name _submission_time :full-name _submission_time
   :label "Submission Time" :type "dateTime"})

(def submitted-by-field
  {:name _submitted_by :full-name _submitted_by
   :label "Submitted by" :type "text"})

(def extra-submission-details [submission-time-field submitted-by-field])

;; Functions on the FIELD object

(defn field-type-in-set?
  "Helper function: is the :type of a field among a set of types"
  [types field]
  (contains? types (:type field)))

(defn group?
  "Checks whether a field in a form (ie, a field) is a group field"
  [field]
  (field-type-in-set? #{"group"} field))

(defn repeat?
  "Checks whether a filed in a form (ie, a field) is a repeat field"
  [field]
  (field-type-in-set? #{"repeat"} field))

(defn numeric?
  "Checks whether a field is a numeric field"
  [field]
  (field-type-in-set? #{"integer" "decimal"} field))

(defn time-based?
  "Checks whether a field is a date or a time field"
  [field]
  (field-type-in-set? #{"date" "time" "dateTime" "start" "end" "today"} field))

(defn categorical?
  "Checks whether a field is a category field (select one or multiple)"
  [field]
  (field-type-in-set? #{"select one" "select all that apply"} field))

(defn select-one?
  "Checks whether a field is a select one field"
  [field]
  (field-type-in-set? #{"select one"} field))

(defn select-all?
  "Checks whether a field is a select multiple (select all that apply) field"
  [field]
  (field-type-in-set? #{"select all that apply"} field))

(defn text?
  "Checks whether a field is a text / string field."
  [field]
  (field-type-in-set? #{"text" "string"} field))

(defn note?
  "Checks whether a field in a form (a field) is a group field"
  [field]
  (field-type-in-set? #{"note"} field))

(defn meta?
  [field]
  (field-type-in-set? #{"start" "end" "today" "deviceid" "imei" "subscriberid"
                        "uuid" "instanceID" "simserial" "phonenumber"} field))

(defn geofield?
  [field]
  (field-type-in-set? #{"geopoint" "gps" "geoshape" "geotrace" "osm"} field))

(defn geopoint?
  [field]
  (field-type-in-set? #{"geopoint" "gps"} field))

(defn geoshape?
  [field]
  (field-type-in-set? #{"geoshape"} field))

(defn image?
  [field]
  (field-type-in-set? #{"image" "photo"} field))

(defn osm?
  [field]
  (field-type-in-set? #{"osm"} field))

(defn calculate?
  "Checks whether a field is a calculate field"
  [field]
  (field-type-in-set? #{"calculate"} field))

(defn has-data?
  "Returns false for fields such as note, group, etc. which don't have data"
  [field]
  (not (or (note? field) (group? field))))

;; Formatting helpers

(defn get-icon [field]
  "Get the icon relevant to the given field (depending on its type)."
  [:i {:class
       (cond
         (text? field)        "fa fa-font"
         (time-based? field)  "fa fa-clock-o"
         (numeric? field)     "fa fa-bar-chart"
         (calculate? field)   "fa fa-bar-chart"
         (categorical? field) "fa fa-bar-chart fa-flip-h-rotate-90"
         :else                "")}])

(defn get-label
  "Gets the label object out of a map with key :label (eg. a field).
   If multiple languages, and none specified, picks out alphabetically first."
  ([labelled-obj] (get-label labelled-obj nil))
  ([{:keys [label name]} &[language]]
   (if-not (map? label)
     (if label label name)
     (if (contains? (-> label keys set) language)
       (label language)
       (label (-> label keys sort first))))))

(defn format-answer
  "String representation for a particular field datapoint (answer).
   re-formatting depends on field type, eg. name->label substitution.
   Optional: compact? should be true if a short string needs to be returned."
  ([field answer] (format-answer field answer nil))
  ([field answer language] (format-answer field answer language false))
  ([field answer language compact?]
   (let [which (cond
                (image? field) :img
                (osm? field) :osm
                (repeat? field) :rpt
                (select-one? field) :sel1
                (select-all? field) :selm
                :else :else)]
     (case which
       :sel1 (if-not answer
               no-answer
               (let [option (->> (:children field)
                                 (filter #(= answer (:name %)))
                                 first)
                     formatted (get-label option language)]
                 (if formatted formatted answer)))
       :selm (if (string/blank? answer)
               no-answer
               (let [names (set (string/split answer #" "))]
                 (->> (:children field)
                      (filter #(contains? names (:name %)))
                      (map #(str "☑ "
                                 (get-label % language) " "))
                      string/join)))
       :img (let [image (:download_url answer)
                  thumb (or (:small_download_url answer) image)
                  fname (last-url-param (:filename answer))]
              (cond
               (or (nil? answer)
                   (string? answer)) answer
               compact?              (format
                                      "<a href='%s' target='_blank'>
                                      <i class='fa fa-external-link'></i>
                                      %s </a>" image fname)
               (nil? thumb)          answer
               :else                 [:a {:href image :target "_blank"}
                                      [:img {:width "80px" :src thumb}]]))
       :osm (when answer
              (let [kw->name name ; aliasing before overriding name
                    {:keys [name type osm-id]} answer
                    type-cap (when type (string/capitalize type))
                    title (str "OSM " type-cap ": " name " (" osm-id ")")]
              (if compact?
                title
                [:table.osm-data
                 [:thead [:th {:col-span 2} title]]
                 [:tbody
                  (map (fn [[tk tv]]
                         (when-not (string/blank? tv)
                           [:tr [:td.question (kw->name tk)] [:td.answer tv]]))
                       (:tags answer))]])))
       :rpt (if (empty? answer)
              ""
              (str "Repeated data with " (count answer) " answers."))
       :else answer))))

(defn relabel-meta-field
  "Try and produce a label for meta field if non-existent."
  [field]
  (let [label (if-let [type (:type field)]
                (case type
                  "start"         "Start time"
                  "end"           "End time"
                  "today"         "Day of survey"
                  "deviceid"      "Device ID (IMEI)"
                  "imei"          "IMEI"
                  "subscriberid"  "IMSI"
                  "simserial"     "SIM serial number"
                  "uuid"          "UUID"
                  "instanceID"    "Instance ID"
                  "phonenumber"   "Phone number"
                  (:name field))
                (case (:name field)
                  _submission_time "Submission time"
                  _submitted_by "Submitted by"
                  ""))]
    (if (:label field) field (assoc field :label label))))

;; UTILITY: Form Flattening

(defn flatten-form
  "Input: map derived from form.json. Output: a flattened vector;
   each element is a field; a field is a {:name .. :label .. :type ..} map.
   By default, REPEAT BLOCKS ARE NOT FLATTENED, repeat blocks represent
   subforms, which need special handling in most cases.
   :flatten-repeats? overrides default behavior, also flattens repeats."
  [form & {:keys [flatten-repeats?]}]
  (letfn [(name-label-map [nd prefix acc]
           (let [{:keys [:type :children :name :label]} nd
                 full-name (if prefix (str prefix "/" name) name)
                 langs (when (map? label) (keys label))
                 nd (with-meta (assoc nd :full-name full-name) {:langs langs})
                 mini-nd (dissoc nd :children)
                 new-children (map #(name-label-map % full-name []) children)]
             (cond
              (group? nd) (concat (conj acc mini-nd) new-children)
              (repeat? nd) (if flatten-repeats?
                             (concat (conj acc mini-nd) new-children)
                             (conj acc (assoc nd :children
                                                 (flatten (apply concat new-children)))))
              :else (conj acc nd))))]
    (let [nodes (flatten (map #(name-label-map % nil []) (:children form)))
          langs (->> nodes (map #(:langs (meta %))) flatten distinct)]
      (with-meta nodes {:languages (remove nil? langs)}))))

;; FUNCTIONS on the flat-form

(defn meta-fields
  "Get just the meta fields out of the form.
   Options to re-label meta fields, or include submission time in meta list."
  [flat-form & {:keys [relabel? with-submission-details?]
                :or   {relabel? true}}]
  (let [meta-fields (filter meta? flat-form)
        include-extra-sub-details (if with-submission-details?
                                    #(into (vec %) extra-submission-details)
                                    identity)
        relabel (if relabel? relabel-meta-field identity)]
    (->> meta-fields
         (map relabel)
         include-extra-sub-details)))

(defn non-meta-fields
  "Get just the fields in this form that are not meta fields."
  [flat-form]
  (remove meta? flat-form))

(defn geofields [flat-form]
  "Get just the geofields from the form."
  (filter geofield? flat-form))

(defn default-geofield [flat-form]
  "From a list of geofields, get the default one to map.
   Implementation: pick first geoshape if any, else pick first geofield."
  (let [geofields (geofields flat-form)
        geoshapes (filter geoshape? geofields)
        geopoints (filter geopoint? geofields)]
    (cond
     (seq geoshapes) (first geoshapes)
     (seq geopoints) (first geopoints)
     :else (first geofields))))

;; UTILITY: languages

(defn english? [language] (re-find #"(?i)english" (str language)))

(defn get-languages [form]
  "Get the languages for a given form."
  (:languages (meta form)))

(defn multilingual? [form]
  "Does this form contain labels in multiple languages?"
  (seq (get-languages form)))

(defn default-lang [languages]
  "Get default language (English or alphabetical first) from within a list."
  (if-let [eng (first (filter english? languages))]
    eng (first (sort languages))))
