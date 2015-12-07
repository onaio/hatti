(ns hatti.charting
  (:require [c2.layout.histogram :refer [histogram]]
            [c2.scale :as scale]
            [c2.svg :as svg]
            [#?(:clj  clj-time.format
                :cljs cljs-time.format) :as tf]
            [#?(:clj  clj-time.coerce
                :cljs cljs-time.coerce) :as tc]
            [#? (:clj clojure.math.numeric-tower
                :cljs hatti.maths) :refer [gcd lcm floor ceil abs]]
            #?(:cljs [hatti.utils :refer [format]])
            [hatti.ona.forms :as f]
            [clojure.string :refer [join blank?]]))

(def millis-in-day 86400000)

(defn- safe-floor
  "Like floor but returns nil if passed nil."
  [n]
  #?(:clj (try
             (floor n)
             (catch IllegalArgumentException e nil))
     :cljs (floor n)))

(defn- style
  "Helper function to create the style argument in hiccup vectors.
  (style :width '200px' :height '100px') => {:style 'width:200px;height:100px'}"
  [style-map]
  {:style #?(:cljs style-map
             :clj  (->> (for [[k v] style-map]
                          (str (name k) ": " v))
                     (join ";" )))})

(defn parse-int
  "Parse an integer from a string."
  [st]
  #?(:clj (when st (if (instance? String st)
                       (when-not (blank? st) (read-string st))
                       st))
     :cljs (let [ans (js/parseInt st)] (if (js/isNaN ans) nil ans))))

(defn str->int [typ]
  "Converts string to integer, for typ (int|date)."
  (case typ
    "int" parse-int
    "date" (fn [date-string]
             (when date-string
               (-> (new js/Date date-string)
                   tc/to-long
                   (/ millis-in-day)
                   safe-floor)))))

(defn int->str [typ &{:keys [digits]
                      :or   {digits 1}}]
  "Converts integers to strings, for type (int|date).
   Optional digits parameter = number of digits after decimal, default is 1."
  (let [int-fmt-s (str "%." digits "f")
        d->millis #(* millis-in-day %)
        date->str #? (:clj  #(tf/unparse (tf/formatters :year-month-day)
                                         (tc/from-long  %))
                      :cljs (fn [date]
                              (when date
                                (.format (js/moment date) "ll"))))]
    (case typ
      "int"  #(format int-fmt-s (float %))
      "date" #(date->str (d->millis %)))))

(defn range->str [[mn mx] typ]
  "Converts a range of typ (int|date) to a string."
  (let [[mn mx] [(ceil mn) (safe-floor mx)]
        fmt (int->str typ :digits 0)]
    (if (<= mx mn) (fmt mn)
      (join " to " [(fmt mn) (fmt mx)]))))

#?(:cljs
   (defn evenly-spaced-bins
     "Given a list of answers, returns each one as a bin, in string form.
   nil is mapped to nil. The bins, in order, are returned as metadata.
   eg. (evenly-spaced-bins [1 2 10] 5 'int') => ['1 to 2' '1 to 2' '9 to 10']
   metadata of this above value would be:
   {:bins ['1 to 2', '3 to 4', '5 to 6', '7 to 8', '9 to 10']}"
     [answers bins typ]
     (let [numbers (->> answers
                        (map (str->int typ)))
           mx (reduce max (remove nil? numbers))
           mn (reduce min (remove nil? numbers))
           s (scale/linear :domain [mn mx] :range [0 (- bins (/ 1 10000))])
           is (map safe-floor (map #(when % (s %)) numbers))
           t (scale/linear :domain [0 bins] :range [mn mx])
           lbounds (->> (range bins) (map t) (map float) distinct)
           ubounds (conj (mapv #(if (= % (safe-floor %))
                                  (dec %) %) (drop 1 lbounds)) mx)
           fmt (int->str typ :digits 0)
           strings (mapv #(range->str [%1 %2] typ) lbounds ubounds)
           results (map (fn [i] (when i (get strings (int i)))) is)
           strings (-> strings distinct vec)] ; remove repeats before output
       (with-meta results
         {:bins (if (contains? (set answers) nil)
                  (conj strings nil)
                  strings)}))))

(defn label-count-pairs
  "Take chart-data from the ona API, returns label->count map.
   eg. Input: {:field_xpath 'D' :data [{:count 2 :D ['Option_1']}]}
   Output: {:Option_1 2}
   eg. Input: {:field_xpath 'D' :data [{:count 1 :D ['O1' 'O_2']
   :count 2 :D ['O1']}]}
   Output: {:Option_1 3 :O_2 1}"
  ([chart-data] (label-count-pairs chart-data nil))
  ([chart-data language]
   (let [{:keys [data field_xpath]} chart-data
         unboxed (for [data-item data]
                   (let [labels ((keyword field_xpath) data-item)
                         count (:count data-item)
                         ;; labelify / get-labels deals with multiple languages
                         labelify #(f/get-label {:label %} language)]
                     (map #(hash-map (labelify %) count) labels)))]
     (->> unboxed
          flatten
          (apply merge-with +)
          (sort-by last >)))))

(defn- num-bins
  "Determine number of bins if there are n possible of values of data.
  Custom algorithm, based on a pleasant range of bins being between
  roughly 7 and 15 (though customizable). Idea is that we try to divide
  n into a number between "
  [n &{:keys [data-type]
       :or   {data-type "int"}}]
  (let [rough-min 7 rough-max 15 real-max 24
        full-range (range rough-min rough-max)
        best-guess (apply max (map (partial gcd n) full-range))]
    (if (< best-guess rough-min)
      (apply (partial min real-max n) (map (partial lcm n) full-range))
      best-guess)))

(defn- extract-data-for-histogram
  "Turn numerical / date chart-data from ona API histogram-friendly.
  Return data looks like [(x dx y)] with-meta {:bins num-bins}."
  [chart-data &{:keys [data-type]
                :or   {data-type "int"}}]
  (let [{:keys [data field_xpath]} chart-data
        retype-fn (str->int data-type)
        qn-key (keyword field_xpath)
        retyped-data (map (fn [el]
                            (update-in el [qn-key] retype-fn))
                          data)
        data-range (- (apply max (map qn-key retyped-data))
                      (apply min (map qn-key retyped-data)))
        bins (if (zero? data-range) 1
               (num-bins data-range :data-type data-type))
        binned-data (histogram retyped-data :value qn-key :bins bins)]
    (with-meta
      (for [data-item binned-data]
        [(:x (meta data-item))
         (:dx (meta data-item))
         (apply + (map :count data-item))])
      {:bins bins})))

(defn counts->lengths
  "Produces a linear mapping [0,max-count] -> [0, max-length], for data which
   is a vector, each element a map with key :count. If total-asmax?, then
   linear map is [0,total-count] -> [0, max-length]."
  [data max-length &{:keys [total-as-max? datamin-as-min?]
                     :or [total-as-max? false datamin-as-min? false]}]
   (let [counts (map :count data)
         xmax (if total-as-max? (reduce + 0 counts) (reduce max 0 counts))
         xmin (if datamin-as-min? (reduce min 0 counts) 0)
         scale (scale/linear :domain [xmin xmax]
                             :range [0 max-length])]
     (map scale counts)))

(defn- response-count-message
  [response-count]
  [:div.t-right.t-grey (str "Based on " response-count " responses.")])

(defn numeric-chart
  "Create numeric (or date) chart out of some chart-data from ona API."
  [chart-data &{:keys [data-type]
                :or   {data-type "int"}}]
  (let [chart-width 700.0 chart-height 300.0
        margin 33.0 small-margin 2.0 y-lim 8.0 neg-margin -15
        extracted-data (extract-data-for-histogram chart-data :data-type data-type)
        {:keys [nil-count non-nil-count]} (meta chart-data)
        bins (:bins (meta extracted-data))
        x-series (map first extracted-data)
        dx-series (map second extracted-data)
        y-series (map last extracted-data)
        xmin (apply min x-series)
        xmax (+ (apply max x-series) (last dx-series))
        x-scale (scale/linear :domain [xmin xmax]
                              :range [0 chart-width])
        y-scale (scale/linear :domain [0 (apply max y-series)]
                              :range [0 chart-height])
        bin-width (safe-floor (- (/ chart-width bins) small-margin))
        x-ticks (take-nth 2 (rest x-series))
        fmt (int->str data-type)]
    (if (= 1 (count extracted-data))
      (let [[value _ total] (first extracted-data)]
        [:div [:p total " records have identical value: " (fmt value)]])
      [:div
       [:svg {:width (+ margin chart-width) :height (+ margin chart-height)}
        [:g.chart {:transform (svg/translate [margin 0])}
         [:g
          (for [[x dx y] extracted-data]
            (let [x-scaled (float (x-scale x))
                  y-scaled (float (y-scale y))
                  [y-scaled txt-ht txt-cls]
                  (if (< 0 y-scaled y-lim) ; y is tiny but positive
                    [small-margin neg-margin "out-of-bar"]
                    [y-scaled small-margin "in-bar"])]
              [:g.bars {:transform
                        (svg/translate
                         [x-scaled (- chart-height y-scaled)])}
               [:g [:rect {:x 1 :height y-scaled :width bin-width}]]
               (when (pos? y-scaled)
                 [:text {:y txt-ht :x (/ bin-width 2.0) :dy "1em"
                         :text-anchor "middle" :class txt-cls} y])]))]
         [:g.axis {:transform (svg/translate [0 chart-height])}
          [:line {:x1 0 :x2 chart-width}]
          [:g (for [x x-ticks]
                [:g.tick {:transform
                          (svg/translate [(float (x-scale x)) 0])}
                 [:text {:y 25 :text-anchor "middle" :class data-type}
                  (fmt x)]
                 [:line {:class "tick" :y2 10 :x2 0}]])]]]]
       (response-count-message non-nil-count)])))

(defn table-chart-h
  [data nil-count non-nil-count field_type]
  "Create category bar chart out of some data + count data. Data of form:
  {'Label1' 1 'Label2' 2}, etc. where the numbers are counts."
  (let [max-count (apply max (vals data))
        percent-s (fn [n total]
                    (let [s (scale/linear :domain [0 total] :range [0 100])]
                      (str (format "%.1f" (float (s n))) "%")))
        select-mult? (= field_type "select all that apply")
        bar-div (if select-mult? :div.bars.select-mult :div.bars.select-one)
        ;; sablono/react.js use col-span
        colspan #? (:clj :colspan :cljs :col-span)
        tdr :td.t-right]
    [:table#bar-chart.table
     [:thead
      [:tr [:th] [:th] [:th.t-right "Count"] [:th.t-right "Percent"]]]
     [:tfoot
           (if select-mult?
         [:tr.t-grey [tdr {colspan 4} (response-count-message non-nil-count)]]
         [:tr.t-grey [tdr] [tdr "Total"] [tdr non-nil-count] [tdr "100%"]])
      (when (and (not select-mult?) (pos? nil-count))
        [:tr.t-grey
         [:td] [:td.t-right "No response"] [:td.t-right nil-count] [:td]])]
     [:tbody
      (for [[label val] data]
        [:tr
         [:td {:title label} label]
         [:td [bar-div (style {:width (percent-s val max-count)})]]
         [:td.t-right val]
         [:td.t-right (percent-s val non-nil-count)]])]]))

(defn extract-nil
  "Removes nil from Ona API chart data; adds nil- and non-nil-count metadata.
   ex. Input:  {:field_xpath 'D' :data [{:D nil :count 5} {:D 1 :count 10}]}
       Output: {:field_xpath 'D' :data [{:D 1 :count 10}]}
               w/ metadata: {:nil-count 5 :non-nil-count 10}"
  [chart-data]
  (let [{:keys [field_xpath data]} chart-data
        na? #(or (nil? %) (= [] %))
        nil-data (first (filter #(na? ((keyword field_xpath) %)) data))
        non-nil-data (remove #(na? ((keyword field_xpath) %)) data)
        nil-count (if-let [n (:count nil-data)] n 0)
        non-nil-count (apply + (map :count non-nil-data))]
    (with-meta (assoc chart-data :data non-nil-data)
      {:nil-count nil-count :non-nil-count non-nil-count})))

(defn make-chart
  "Make chart depending on datatype."
  ([chart-data] (make-chart chart-data nil))
  ([chart-data language]
   (let [{:keys [field_label data_type field_xpath field_type]} chart-data
         chart-data (extract-nil chart-data)
         {:keys [nil-count non-nil-count]} (meta chart-data)
         not-supported #(vector :div.t-red
                                (str "Aplogies. At the moment, making a chart of
                                     this data type (" % ") is not supported."))
         chart (if (zero? non-nil-count)
                 [:p "No data"]
                 (case data_type
                   "categorized" (table-chart-h (label-count-pairs chart-data language)
                                                nil-count
                                                non-nil-count
                                                field_type)
                   "time_based"  (numeric-chart chart-data
                                                :data-type "date")
                   "numeric"     (numeric-chart chart-data)
                   (not-supported data_type)))]
     {:label field_label :name field_xpath
      :chart [:div chart ]})))
