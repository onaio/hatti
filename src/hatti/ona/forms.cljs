(ns hatti.ona.forms
  (:require [chimera.js-interop :refer [format]]
            [chimera.urls :refer [last-url-param]]
            [cljs.pprint :refer [cl-format]]
            [clojure.string :as string]
            [hatti.constants :refer [_submission_time
                                     _submitted_by
                                     _last_edited]]))

;; CONSTANTS
(def currency-regex #"£|$")
(def newline-regex #"[\n\r]")
(def no-answer "No Answer")

(def submission-time-field
  {:name _submission_time
   :full-name _submission_time
   :label "Submission Time"
   :type "dateTime"})

(def submitted-by-field
  {:name _submitted_by
   :full-name _submitted_by
   :label "Submitted by"
   :type "text"})

(def last_edited
  {:name _last_edited
   :full-name _last_edited
   :label "Last Edited"
   :type "date"})

(def extra-submission-details [last_edited
                               submission-time-field
                               submitted-by-field])

;; Formatting helpers
(defn- format-multiline-answer
  "Format multiline answer by introducing html line breaks"
  [answer]
  (if (and (string? answer) (re-find newline-regex answer))
    (map #(vector :p %) (string/split answer newline-regex))
    answer))

;; Functions on the FIELD object
(defn field-type-in-set?
  "Helper function: is the :type of a field among a set of types"
  [types field]
  (contains? types (:type field)))

(defn field-name-in-set?
  "Helper function: is the :name of a field among a set of names"
  [names field]
  (contains? names (:name field)))

(defn group?
  "Checks whether a field in a form (ie, a field) is a group field"
  [field]
  (field-type-in-set? #{"group"} field))

(defn repeat?
  "Checks whether a field in a form (ie, a field) is a repeat field"
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
  (or (field-name-in-set? #{"meta" "instanceID"} field)
      (field-type-in-set? #{"deviceid"
                            "end"
                            "imei"
                            "instanceID"
                            "phonenumber"
                            "simserial"
                            "start"
                            "subscriberid"
                            "today"
                            "uuid"}
                          field)))

(defn geofield?
  [field]
  (or (field-type-in-set? #{"geopoint" "gps" "geoshape" "geotrace" "osm"}
                          field)
      (when (repeat? field)
        (let [{:keys [children]} field]
          (some geofield? children)))))

(defn geopoint?
  [field]
  (or (field-type-in-set? #{"geopoint" "gps"} field)
      (when (repeat? field)
        (let [{:keys [children]} field]
          (some geopoint? children)))))

(defn geoshape?
  [field]
  (field-type-in-set? #{"geoshape"} field))

(defn geotrace?
  [field]
  (field-type-in-set? #{"geotrace"} field))

(defn image?
  [field]
  (field-type-in-set? #{"image" "photo"} field))

(defn video?
  [field]
  (field-type-in-set? #{"video"} field))

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
(defn get-icon
  "Get the icon relevant to the given field (depending on its type)."
  [field]
  [:i {:class
       (cond
         (text? field)        "fa fa-font"
         (time-based? field)  "fa fa-clock-o"
         (numeric? field)     "fa fa-bar-chart"
         (calculate? field)   "fa fa-bar-chart"
         (categorical? field) "fa fa-bar-chart fa-flip-h-rotate-90"
         :else                "")}])

(defn get-column-class
  "Assign class according to field type category, e.g. integer & decimals are
   both in the numeric category"
  [field]
  (cond
    (text? field)        "column-string"
    (numeric? field)     "column-numeric"
    (time-based? field)  "column-datetime"
    (categorical? field) "column-categorical"
    (geofield? field)    "column-geofield"
    (image? field)       "column-image"
    (video? field)       "column-video"
    (meta? field)        "column-metadata"
    :else                ""))

(defn get-label
  "Gets the label object out of a map with key :label (eg. a field).
   If multiple languages, and none specified, picks out alphabetically first."
  [{:keys [label name]} & [language]]
  (if-not (map? label)
    (or label name)
    (if (contains? (-> label keys set) language)
      (label language)
      (label (-> label keys sort first)))))

(defn format-answer
  "String representation for a particular field datapoint (answer).
   re-formatting depends on field type, eg. name->label substitution.
   Optional: compact? should be true if a short string needs to be returned."
  [field answer & {:keys [language compact? label field-key]
                   :or {field-key :name}}]
  (cond
    (string/blank? answer) no-answer
    (select-one? field) (let [option (->> (:children field)
                                          (filter #(= answer (field-key %)))
                                          first)
                              formatted (get-label option language)]
                          (or formatted answer))
    (select-all? field) (let [names (set (string/split answer #" "))]
                          (->> (:children field)
                               (filter #(contains? names (field-key %)))
                               (map #(str "☑ " (get-label % language) " "))
                               string/join))
    (time-based? field) (-> answer js/moment (.format "ll"))
    (or (image? field)
        (video? field)) (let [image (:download_url answer)
                              thumb (or (:small_download_url answer) image)
                              fname (last-url-param (:filename answer))]
                          (cond
                            (string? answer) answer
                            compact? (format "<a href='%s' target='_blank'>
                                      <i class='fa fa-external-link'></i>
                                      %s </a>" image fname)
                            (nil? thumb) answer
                            :else [:a {:href image :target "_blank"}
                                   (if (image? field)
                                     [:img {:width "80px" :src thumb}]
                                     [:span
                                      [:i.fa.fa-file-video-o]
                                      " " fname])]))
    (osm? field) (let [kw->name name ; aliasing before overriding name
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
                                [:tr
                                 [:td.question (kw->name tk)]
                                 [:td.answer tv]]))
                            (:tags answer))]]))
    (repeat? field) (if (empty? answer)
                      no-answer
                      (str "Repeated data with " (count answer) " answers."))
    ;; Otherwise it is text of some kind
    :else (if (numeric? field)
            (if-let [currency (some->> label (re-find currency-regex))]
              (str currency (cl-format nil "~:d" answer))
              answer)
            (format-multiline-answer answer))))

(defn relabel-meta-field
  "Try and produce a label for meta field if non-existent."
  [{:keys [label name type] :as field}]
  (let [label (if type
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
                  name)
                (case name
                  _submission_time "Submission time"
                  _submitted_by    "Submitted by"
                  ""))]
    (cond-> field (not label) (assoc :label label))))

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
                               (conj acc
                                     (assoc nd :children
                                            (flatten
                                             (apply concat new-children)))))
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

(defn geofields
  "Get just the geofields from the form."
  [flat-form]
  (filter geofield? flat-form))

(defn default-geofield
  "From a list of geofields, get the default one to map.
   Implementation: pick first geoshape if any, else pick first geofield."
  [flat-form]
  (let [repeats (->> flat-form
                     (filter repeat?)
                     flatten)
        geofields (geofields (concat flat-form repeats))
        geoshapes (filter geoshape? geofields)
        geopoints (filter geopoint? geofields)]
    (cond
      (seq geoshapes) (first geoshapes)
      (seq geopoints) (first geopoints)
      :else (first geofields))))

;; UTILITY: languages
(defn english? [language] (re-find #"(?i)english" (str language)))

(defn get-languages
  "Get the languages for a given form."
  [form]
  (:languages (meta form)))

(defn multilingual?
  "Does this form contain labels in multiple languages?"
  [form]
  (seq (get-languages form)))

(defn default-lang
  "Get default language (English or alphabetical first) from within a list."
  [languages]
  (if-let [eng (first (filter english? languages))]
    eng (first (sort languages))))
