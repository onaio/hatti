(ns hatti.utils
  (:require [clojure.string :refer [split join]]
            [cognitect.transit :as t]
            [goog.string.format]
            [goog.string]
            [inflections.core :refer [plural]]))

(defn last-url-param
  "Get last parameter form url"
  [url]
  (let [last-param (-> url str (split #"/") last)]
    (-> last-param str (split #".json") first)))

(defn json->cljs [s]
  "Convert json string to cljs object using transit.
   Fast, but doesn't preserve keywords."
  (t/read (t/reader :json) s))

(defn json->js [s]
  "Convert json to js using JSON.parse"
  (.parse js/JSON s))

(defn json->js->cljs [s]
  "Convert json string to cljs via js.
   Slow method, but preserves keywords, and appropriate for small json."
  (js->clj (json->js s) :keywordize-keys true))

(defn format
  "Formats a string using goog.string.format, so we can use format in cljx."
  [fmt & args]
  (apply goog.string/format fmt args))

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
