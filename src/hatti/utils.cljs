(ns hatti.utils
  (:require [clojure.string :refer [split join]]
            [clojure.string :refer [join lower-case split]]
            [goog.string.format]
            [goog.string]
            [inflections.core :refer [plural]]))

(defn url [& args] (clojure.string/join "/" args))

(defn last-url-param
  "Get last parameter form url"
  [url]
  (let [last-param (-> url str (split #"/") last)]
    (-> last-param str (split #".json") first)))

(defn json->js [s]
  "Convert json to js using JSON.parse"
  (.parse js/JSON s))

(defn json->cljs [s]
  "Convert json string to cljs object.
   Fast, but doesn't preserve keywords."
   (js->clj (json->js s)))

(defn json->js->cljs [s]
  "Convert json string to cljs via js.
   Slow method, but preserves keywords, and appropriate for small json."
  (js->clj (json->js s) :keywordize-keys true))

(defn format
  "Formats a string using goog.string.format, so we can use format in cljx."
  [fmt & args]
  (apply goog.string/format fmt args))

(defn safe-regex [s & {:keys [:ignore-case?]
                       :or    {:ignore-case? true}}]
  "Create a safe (escaped) js regex out of a string.
   By default, creates regex with ignore case option."
  (let [s (.replace s
                    #"/[\-\[\]\/\{\}\(\)\*\+\?\.\\\^\$\|]/"
                    "\\$&")] ;; See http://stackoverflow.com/a/6969486
    (if :ignore-case?
      (js/RegExp. s "i")
      (js/RegExp. s))))

(defn indexed [coll]
  "Given a seq, produces a two-el seq. [a b c] => [[0 a] [1 b] [2 c]]."
  (map-indexed vector coll))

(defn click-fn [f]
  "Helper function to create a click function that prevents default"
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
