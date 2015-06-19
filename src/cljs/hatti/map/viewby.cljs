(ns hatti.map.viewby
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [clojure.string :as s]
            [hatti.utils :refer [safe-regex]]
            [hatti.charting :refer [evenly-spaced-bins]]
            [hatti.ona.forms :as f]
            [hatti.map.utils :as mu]))

;;;;; MAP

(def qualitative-palette
  "Saturated qualitative palette from colorbrewer.
   Saturated red+brown disabled due to clash with the :clicked color."
  ["#1f78b4"
   "#33a02c"
   ;"#e31a1c"
   "#6a3d9a"
   ;"#ff7f00"
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
    (let [id (mu/get-id marker)
          geotype (mu/marker->geotype marker)
          color-key (if (= :line geotype) :color :fillColor)]
      {color-key (if (id-selected? id) (id-color id) grey)})))

(defn- move-nil-to-end [s]
  (let [no-nil (into [] (remove nil? s))]
    (if (= s no-nil) s (conj no-nil nil))))

(defn all-but-nil-selected
  [answers]
  (merge (zipmap answers (repeat true)) {nil false}))

(defn preprocess-answers [field raw-answers]
  "Preproccesses answers depending on the field. For multi-selects, return type
   is a list of list of strings. For other types, a list of strings."
  (cond
    (f/select-one? field) raw-answers
    (f/text? field) raw-answers
    (f/calculate? field) raw-answers
    (f/numeric? field) (evenly-spaced-bins raw-answers 5 "int")
    (f/time-based? field) (evenly-spaced-bins raw-answers 5 "date")
    (f/select-all? field) (->> raw-answers
                               (map #(when % (s/split % #" "))))))

(defn field->colors [field]
  "Returns the appropriate set of colors given the field."
  (cond
    ;; if too many options w/in select-one field, fall back to select-all style
    (f/select-one? field) (if (<= (count (:children field))
                                  (count qualitative-palette))
                            qualitative-palette
                            (repeat "#f30"))
    (f/calculate? field)  qualitative-palette
    (f/numeric? field)    sequential-palette
    (f/time-based? field) sequential-palette
    (f/text? field) (repeat "#f30")
    (f/select-all? field) (repeat "#f30")))

(defn viewby-info
  "Produces a set of data structures / functions for view-by.
   answers are a list of answers, sorted by count;
   id->answers is a mapping from id to either one or many answers
   answer->count, answer->selected?, answer->color are maps from answer;
   used for the legend rendering. An 'answer' is mapped from a data element,
   eg. a bin for numbers/dates, an option for multiple/single selects."
  [field raw-answers ids]
  (let [fname (:full-name field)
        ans-s (preprocess-answers field raw-answers)
        answer->count (frequencies (flatten ans-s))
        sorted (cond
                 (or (f/categorical? field)
                     (f/text? field)
                     (f/calculate? field)) (map first
                                                (sort-by second > answer->count))
                (or (f/time-based? field) (f/numeric? field)) (-> ans-s meta :bins))
        sorted-nil-at-end (move-nil-to-end sorted)
        colors (field->colors field)
        defaults {:answers sorted-nil-at-end
                  :id->answers (zipmap ids ans-s)
                  :answer->count answer->count
                  :answer->selected? (all-but-nil-selected sorted)
                  :answer->color (zipmap sorted colors)
                  :field field}]
    (cond
      (f/select-all? field) (merge defaults {:id-color #(first colors)})
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
      (mu/re-style-marker m->s marker)
      (mu/bring-to-top-if-selected id-selected? marker))))

(defn filtered-answer-selections
  [answers query]
  "Given a list of answers + query, returns map from answers to true/false.
   True if query is in the answer, false if not."
  (let [text-is-in-answer (map #(re-find (safe-regex query) %) answers)]
    (zipmap answers text-is-in-answer)))

(defn toggle-answer-selected
  "This function appropriately toggles answer->selected? when answer is clicked
   answer->selected? is a map from answers to true/false. Special rules:
   First click = select the answer. If nothing clicked, make everything clicked."
  [answer->selected? answer]
  (let [answers (keys answer->selected?)
        all-false (zipmap answers (repeat false))]
    (if (nil? answer) ; nil cannot be selected or deselected
      answer->selected?
      (if (= (all-but-nil-selected answers) answer->selected?)
        (merge all-false {answer true})
        (let [new (update-in answer->selected? [answer] not)]
          (if (= new all-false) (all-but-nil-selected answers) new))))))
