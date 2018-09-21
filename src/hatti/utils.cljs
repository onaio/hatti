(ns hatti.utils
  (:require [clojure.string :refer [join lower-case split upper-case replace]]
            [goog.string.format]
            [goog.string]
            [inflections.core :refer [plural]]
            [sablono.core :refer-macros [html] :refer [render-static]]))

(defn json->js
  "Convert json to js using JSON.parse"
  [s]
  (.parse js/JSON s))

(defn indexed
  "Given a seq, produces a two-el seq. [a b c] => [[0 a] [1 b] [2 c]]."
  [coll]
  (map-indexed vector coll))

(defn click-fn
  "Helper function to create a click function that prevents default"
  [f]
  (fn [event] (.preventDefault event) (f)))

(defn pluralize-number
  "Create an appropriately pluralized string prefix by number."
  [number kind]
  (join " " [number (if (= 1 number) kind (plural kind))]))

(defn ^boolean substring?
  "True if substring is a substring of string"
  ([substring string]
   ((complement nil?) (re-find (re-pattern substring) string)))
  ([substring string & {:keys [case-sensitive?]}]
   (if case-sensitive?
     (substring? substring string)
     (substring? (lower-case substring) (lower-case string)))))

(defn hyphen->camel-case
  [source-string]
  (replace source-string
           #"(-)(.)"
           #(let [[_ _ letter-to-uppercase] %]
              (upper-case letter-to-uppercase))))

(defn- generate-component
  [element-definition-vector]
  (html element-definition-vector))

(defn generate-html
  "This function generates HTML from hiccup-style vectors, and concatenates
  the resulting markup. Strings are returned unaffected."
  [& element-definition-vectors]
  (let [components (map generate-component element-definition-vectors)
        components-as-static-markup (map
                                     (fn
                                       [component]
                                       (if (string? component)
                                         component
                                         (render-static component)))
                                     components)]
    (join components-as-static-markup)))
