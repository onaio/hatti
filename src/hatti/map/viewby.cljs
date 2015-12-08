(ns hatti.map.viewby
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [clojure.string :refer [split]]
            [hatti.utils :refer [safe-regex]]
            [hatti.charting :refer [evenly-spaced-bins]]
            [hatti.ona.forms :as form-utils]
            [hatti.map.utils :as map-utils]))

;;;;; MAP

(def qualitative-palette
  "Saturated qualitative palette from colorbrewer.
   Saturated red+brown disabled due to clash with the :clicked color."
  ["#1f78b4"
   "#33a02c"
   "#6a3d9a"
   "#ffff99"
   "#b15928"
   "#a6cee3"
   "#b2df8a"
   "#fb9a99"
   "#fdbf6f"
   "#cab2d6"])

(def sequential-palette
  "Color palette of YlGnBl from colorbrewer."
  ["#ffffcc" "#bdd7e7" "#6baed6" "#3182bd" "#08519c"])

(def grey
  "A Grey Color for deselected points."
  "#d9d9d9")

(defn- marker-styler
  [id-color id-selected?]
  (fn [marker]
    (let [id (map-utils/get-id marker)
          geotype (map-utils/marker->geotype marker)
          color-key (if (= :line geotype) :color :fillColor)]
      {color-key (if (id-selected? id) (id-color id) grey)})))

(defn- move-nil-to-end [s]
  (let [no-nil (vec (remove nil? s))]
    (if (= s no-nil) s (conj no-nil nil))))

(defn all-but-nil-selected
  [answers]
  (merge (zipmap answers (repeat true)) {nil false}))

(defn preprocess-answers
  "Preproccesses answers depending on the field. For multi-selects, return type
   is a list of list of strings. For other types, a list of strings."
  [field raw-answers]
  (cond
    (form-utils/select-one? field) raw-answers
    (form-utils/text? field) raw-answers
    (form-utils/calculate? field) raw-answers
    (form-utils/numeric? field) (evenly-spaced-bins raw-answers 5 "int")
    (form-utils/time-based? field) (evenly-spaced-bins raw-answers 5 "date")
    (form-utils/select-all? field) (->> raw-answers
                               (map #(when % (split % #" "))))))

(defn get-user-defined-palette
  "Return the appropriate set of colors for a select_one"
  [{:keys [children]}]
  (when (every? :appearance children)
    (map :appearance children)))

(defn field->colors
  "Returns the appropriate set of colors given the field."
  [field]
  (cond
    ;; if too many options w/in select-one field, fall back to select-all style
    (form-utils/select-one? field)
    (if-let [user-defined-palette (get-user-defined-palette field)]
      user-defined-palette
      (if (<= (count (:children field))
              (count qualitative-palette))
        qualitative-palette
        (repeat "#f30")))
    (form-utils/calculate? field)  qualitative-palette
    (form-utils/numeric? field)    sequential-palette
    (form-utils/time-based? field) sequential-palette
    (form-utils/text? field) (repeat "#f30")
    (form-utils/select-all? field) (repeat "#f30")))

(defn viewby-info
  "Produces a set of data structures / functions for view-by.
   answers are a list of answers, sorted by count;
   id->answers is a mapping from id to either one or many answers
   answer->count, answer->selected?, answer->color are maps from answer;
   used for the legend rendering. An 'answer' is mapped from a data element,
   eg. a bin for numbers/dates, an option for multiple/single selects."
  [field raw-answers ids]
  (let [fname (:full-name field)
        preprocessed-answers (preprocess-answers field raw-answers)
        answer->count (frequencies (flatten preprocessed-answers))
        sorted-answers (cond
                 (or (form-utils/categorical? field)
                     (form-utils/text? field)
                     (form-utils/calculate? field))
                 (map first (sort-by second > answer->count))
                (or (form-utils/time-based? field) (form-utils/numeric? field))
                (-> preprocessed-answers meta :bins))
        sorted-answers-with-nil-at-end (move-nil-to-end sorted-answers)
        colors (field->colors field)
        defaults {:answers sorted-answers-with-nil-at-end
                  :id->answers (zipmap ids preprocessed-answers)
                  :answer->count answer->count
                  :answer->selected? (all-but-nil-selected sorted-answers)
                  :answer->color (zipmap sorted-answers colors)
                  :visible-answers sorted-answers-with-nil-at-end
                  :field field}]
    (cond
      (form-utils/select-all? field) (merge defaults {:id-color #(first colors)})
      :else                 defaults)))

(defn id-color-selected
  "Generates id-color and id-selected? functions based on viewby-info."
  [{:keys [field id->answers answer->color answer->selected?]}]
  (case (:type field)
    "select all that apply"
      {:id-color #(first ["#f30"])
       :id-selected? (fn [id]
                       (let [answers (id->answers id)]
                         (if (nil? answers)
                           (get answer->selected? answers)
                           (some identity (map answer->selected? answers)))))}
    ;; For all other types, answer is a scalar, not a list
    {:id-color #(-> % id->answers answer->color)
     :id-selected? #(-> % id->answers answer->selected?)}))

(defn view-by!
  [view-by-info markers]
  (let [{:keys [id-selected? id-color]} (id-color-selected view-by-info)
        m->s (marker-styler id-color id-selected?)]
    (doseq [marker markers]
      (map-utils/re-style-marker m->s marker)
      (map-utils/bring-to-top-if-selected id-selected? marker))))

(defn filter-answer-data-structures
  "Given a list of answers + query, returns map from answers to true/false.
   True if query is in the answer, false if not."
  [answers query field language]
  (let [query-present? (fn [ans]
                         (when ans
                           (re-find (safe-regex query)
                                    (form-utils/format-answer field ans language))))]
    {:visible-answers (filter query-present? answers)
     :answer->selected? (zipmap answers (map query-present? answers))}))

(defn toggle-answer-selected
  "This function appropriately toggles answer->selected? when answer is clicked
   answer->selected? is a map from answers to true/false. Special rules:
   First click = select the answer. If nothing clicked, make everything
   clicked."
  [answer->selected? visible-answers answer]
  (let [all-answers (vals answer->selected?)
        all-visible-selected? (fn [a->s visible]
                                (= (set visible)
                                   (->> a->s (filter second) keys set)))]
    (if (nil? answer)
      ;; nil cannot be selected or deselected: no-change
      answer->selected?
      ;; else -> the logic begins
      (if (all-visible-selected? answer->selected? visible-answers)
        ;; first click -> *just* select this answer
        (merge (zipmap all-answers (repeat false)) {answer true})
        ;; not first click ->  toggle this answer, leave the rest
        (let [toggled (update-in answer->selected? [answer] not)]
          (if (every? false? (vals toggled))
            ;; special rule: nothing is clicked -> make everything clicked
            (merge (zipmap all-answers (repeat false))
                   (all-but-nil-selected visible-answers))
            ;; else: some things are clicked -> leave alone
            toggled))))))
