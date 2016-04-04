(ns hatti.utils.style
  (:require [clojure.string :refer [join split]]
            [hatti.ona.forms :as form-utils]))

(def user-customizable-styles #{"color"})

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

(defn customizable-style?
  [[style-name _]]
  (contains? user-customizable-styles style-name))

(defn get-css-rule-map
  "Return a map of a form field answer name to CSS rules"
  [appearance-attribute]
  (->> (split appearance-attribute #";")
       (map #(split % #":"))
       (filter customizable-style?)
       (map (fn [[key value]]
              [(keyword key) value]))
       (into {})))

(defn group-user-defined-styles-by-answer
  "Return a map "
  [{:keys [children]}]
  (->> children
       (map (fn [{:keys [name appearance]}]
              {name (get-css-rule-map appearance)}))
       (apply merge)))

(defn- style-map->color-map
  [[answer style]]
  {answer (:color style)})

(defn group-user-defined-colors-by-answer
  [field]
  (->> field
       group-user-defined-styles-by-answer
       (map style-map->color-map)
       (into {})))

(defn field->colors
  "Return the appropriate set of colors given the field. For a select_one,
   returns a mapping of answer to color. Returns a string for all other field
   types"
  [field]
  (cond
    ;; if too many options w/in select-one field, fall back to select-all style
    (form-utils/select-one? field)
    (if (<= (count (:children field))
            (count qualitative-palette))
      qualitative-palette
      (repeat "#f30"))
    (form-utils/calculate? field)  qualitative-palette
    (form-utils/numeric? field)    sequential-palette
    (form-utils/time-based? field) sequential-palette
    (form-utils/text? field) (repeat "#f30")
    (form-utils/select-all? field) (repeat "#f30")))

(defn answer->color
  [{:keys [children] :as field} answers]
  (cond
    (and (form-utils/select-one? field)
         (every? :appearance children))
    (group-user-defined-colors-by-answer field)
    :else (zipmap answers (field->colors field))))
