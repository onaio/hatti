(ns ona.dataview.chart-test
  (:require-macros [cljs.test :refer (is deftest testing)]
                   [dommy.macros :refer [node sel sel1]])
  (:require [cljs.test :as t]
            [cljs.core.async :refer [<! chan put!]]
            [dommy.core :as dommy]
            [ona.dataview.shared :as shared]
            [ona.dataview.base :as dv]
            [ona.dataview.chart :as cv]
            [ona.utils.dom :refer [new-container!]]
            [ona.dataview.dommy-helpers :as dh]
            [ona.utils.forms :as f]
            [ona.helpers.permissions :refer [owner readonly]]
            [om.core :as om :include-macros true]
            [ona.utils.seq :refer [diff]]
            [ona.dataview.shared-test :refer [fat-form no-data small-fat-data data-gen]]))

;; CHART COMPONENT HELPERS

(def chart-form (take 4 fat-form))
(def chart-get-mock #(let [c (chan)] (put! c {:body nil}) c))

(defn- chart-container [form]
  "Returns a container in which a map component has been rendered.
   `data` arg is directly passed into the component as its cursor."
  (let [cont (new-container!)
        arg {:shared {:flat-form form
                      :event-chan (chan)}
             :opts {:chart-get chart-get-mock}
             :target cont}
        _ (om/root cv/chart-page shared/app-state arg)]
    cont))

;; COMPONENT TESTS

(deftest charts-render-properly
  (let [container (chart-container chart-form)
        sel1qs (filter f/categorical? chart-form)
        stringqs (filter f/text? chart-form)]
    (testing "chart-chooser menu renders properly"
      (is (every? (-> container (sel :li.submenu-list) dh/texts set)
                  (map :label chart-form)))
      ;; First icon is a clock, for submission time
      (is (= "fa fa-clock-o"
             (-> container (sel1 :ul) (sel :i.fa) first (dommy/attr :class))))
      ;; Rest of the items should be horizontal chart (for select one)
      (is (= (repeat (count sel1qs) "fa fa-bar-chart fa-flip-h-rotate-90")
             (->> (-> container (sel1 :ul) (sel :i.fa) rest)
                  (map #(dommy/attr % :class))))))
    (testing "string questions are unclickable on the chart menu"
      (let [stringq-texts (map :label stringqs)
            num-stringqs (count stringqs)
            txt->href (into {} (map #(vector (dommy/text %) (dommy/attr % :href))
                                    (sel container :a)))]
        (is (= (repeat num-stringqs nil)
               (map val (select-keys txt->href stringq-texts))))))
    (testing "chart-container displays submission time chart initially"
      (is (= 1 (count (sel container :div.chart-holder))))
      (is (= "Submission Time" (dommy/text (sel1 container :h3.chart-name)))))))

(deftest charts-renders-correct-language
  (let [chart-form [{:type "select one" :name "foo" :full-name "foo"
                     :label {:French "Oui?" :English "Yes?"}}]
        _ (swap! shared/app-state assoc-in [:languages]
                 {:all [:English :French] :current :French})
        container (chart-container chart-form)]
    (testing "chart menu renders in current language when set"
      (is (contains? (-> container (sel :li.submenu-list) dh/texts set) "Oui?")))))
