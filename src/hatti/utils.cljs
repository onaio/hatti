(ns hatti.utils
  (:require [clojure.string :refer [join lower-case split upper-case replace]]
            [goog.string.format]
            [goog.string]
            [inflections.core :refer [plural]]))

(defn url [& args] (clojure.string/join "/" args))

(defn last-url-param
  "Get last parameter form url"
  [url]
  (let [last-param (-> url str (split #"/") last)]
    (-> last-param str (split #".json") first)))

(defn json->js
  "Convert json to js using JSON.parse"
  [s]
  (.parse js/JSON s))

(defn json->cljs
  "Convert json string to cljs object.
   Fast, but doesn't preserve keywords."
  [s]
   (js->clj (json->js s)))

(defn json->js->cljs
  "Convert json string to cljs via js.
   Slow method, but preserves keywords, and appropriate for small json."
  [s]
  (js->clj (json->js s) :keywordize-keys true))

(defn format
  "Formats a string using goog.string.format, so we can use format in cljx."
  [fmt & args]
  (apply goog.string/format fmt args))

(defn safe-regex
  "Create a safe (escaped) js regex out of a string.
   By default, creates regex with ignore case option."
  [s & {:keys [:ignore-case?]
        :or {:ignore-case? true}}]
  (let [s (.replace s
                    #"/[\-\[\]\/\{\}\(\)\*\+\?\.\\\^\$\|]/"
                    "\\$&")] ;; See http://stackoverflow.com/a/6969486
    (if :ignore-case?
      (js/RegExp. s "i")
      (js/RegExp. s))))

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
  (js/console.log source-string)
  (replace source-string
           #"(-)(.)"
           #(let [[_ _ letter-to-uppercase] %]
              (upper-case letter-to-uppercase))))
