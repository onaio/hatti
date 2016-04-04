(ns hatti.utils.style-test
  (:require-macros [cljs.test :refer [is deftest testing]])
  (:require [clojure.string :refer [join]]
            [hatti.utils.style :refer [customizable-style?
                                     get-css-rule-map
                                     group-user-defined-colors-by-answer
                                     group-user-defined-styles-by-answer
                                     user-customizable-styles]]))

(def customizable-style [(first user-customizable-styles)
                         "style-definition"])
(def non-customizable-style ["non-customizable" "style-definition"])

(def appearance-attribute
  (join ";" [(join ":" customizable-style)
             (join ":" non-customizable-style)]))

(def field-name "field-name")
(def answer-name "answer-name")

(def css-rule-map
  {(-> customizable-style first keyword) "style-definition"})

(def field {:name field-name
            :full-name field-name
            :type "select_one"
            :children [{:appearance "color:green;font-weight:bold;"
                        :name "good"}
                       {:appearance "color:red;"
                        :name "bad"}]})

(deftest test-customizable-style?
  (testing "returns whether a style representation is customizable"
    (is (customizable-style? customizable-style))
    (is (not (customizable-style? non-customizable-style)))))

(deftest test-get-css-rule-map
  (testing "returns a mapping of name to approved styles"
    (is (= (get-css-rule-map appearance-attribute)
           css-rule-map))))

(deftest test-group-user-defined-styles-by-answer
  (testing "returns a map of user-defined styles grouped by answer"
    (let [result {"good" {:color "green"}
                  "bad" {:color "red"}}]
      (is (= (group-user-defined-styles-by-answer field)
             result)))))

(deftest test-group-user-defined-colors-by-answer
  (testing "returns a map of answer to color"
    (is (= (group-user-defined-colors-by-answer field)
           {"good" "green" "bad" "red"}))))
