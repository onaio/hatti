(ns hatti.ona.forms-test
  (:require-macros [cljs.test :refer (is deftest testing)]
                   [hatti.macros :refer [read-file]])
  (:require [cljs.core.async :refer [chan]]
            [clojure.string :as string]
            [hatti.utils :as utils]
            [hatti.ona.forms :as forms]))


(defn get-form [which]
  (let [file (case which
               :single-language   (read-file "test/fixtures/mini-form-9-elements.json")
               :multi-langauge    (read-file "test/fixtures/multi-language-mini-form.json")
               :geoshape          (read-file "test/fixtures/geoshape-form.json")
               :geopoint          (read-file "test/fixtures/geopoint-form.json")
               :geotrace          (read-file "test/fixtures/geotrace-form.json")
               :groups-in-repeats (read-file "test/fixtures/groups-in-repeats-form.json"))]
    (utils/json->js->cljs file)))

(defn get-data [which]
  (let [file (case which
                :geoshape (read-file "test/fixtures/geoshape-data.json")
                :geopoint (read-file "test/fixtures/geopoint-data.json")
                :geotrace (read-file "test/fixtures/geotrace-data.json"))]
    (utils/json->js->cljs file)))

(def single-language-form (get-form :single-language))
(def multi-language-form (get-form :multi-langauge))
(def groups-in-repeats-form (get-form :groups-in-repeats))
(def geoshape-form (get-form :geoshape))
(def geopoint-form (get-form :geopoint))
(def geotrace-form (get-form :geotrace))

(deftest flatten-form-generates-full-names-correctly
  (let [flat-form (forms/flatten-form single-language-form)
        full-names (map :full-name flat-form)
        names (map :name flat-form)]
    (is (= (count flat-form) 9))
    (is (= (count (filter identity full-names)) 9))
    (is (= (count (filter #(re-find #"informed_consent" %) full-names)) 7))
    (is (= (filter nil? (map #(re-find (re-pattern %1) %2) names full-names)) ()))))

(deftest flatten-form-deals-with-repeats-properly
  (let [group->repeat #(if (forms/group? %) (merge % {:type "repeat"}) %)
        form-with-repeat (assoc single-language-form :children
                                                     (map group->repeat (:children single-language-form)))
        flattened1 (forms/flatten-form form-with-repeat)
        flattened2 (forms/flatten-form form-with-repeat :flatten-repeats? true)
        flattened3 (forms/flatten-form groups-in-repeats-form)
        repeat1 (first (filter forms/repeat? flattened1))
        repeat2 (first (filter forms/repeat? flattened2))
        repeat3 (:children (first (filter forms/repeat? flattened3)))
        with-consent #(re-find #"informed_consent" (:full-name %))]
    (testing "We have 3 top-level elements without :flatten-repeats?, 9 with."
      (is (= (count flattened1) 3))
      (is (= (count flattened2) 9))
      (is (= (->> flattened1 (filter with-consent) count) 1))
      (is (= (->> flattened2 (filter with-consent) count)) 7))
    (testing ":full-name of sub-repeat elements are same with + w/o flattening"
      (is (= (map :full-name (:children (last flattened1))) (drop 3 (map :full-name flattened2)))))
    (testing "groups in repeats are flattened"
      (is (= (count repeat3) 3)))))

(deftest regarding-language-utilties
  (let [flat-form-1 (forms/flatten-form single-language-form)
        flat-form-2 (forms/flatten-form multi-language-form)]
    (testing "flatten-form picks out languages correctly"
      (is (= (-> flat-form-1 meta :languages) ()))
      (is (= (-> flat-form-2 meta :languages set) #{:English :French}))
      (is (= (-> flat-form-1 forms/get-languages) ()))
      (is (= (-> flat-form-2 forms/get-languages set) #{:English :French})))
    (testing "multilingual? works"
      (is (not (forms/multilingual? flat-form-1)))
      (is (forms/multilingual? flat-form-2)))
    (testing "english? works"
      (is (forms/english? :English))
      (is (forms/english? "ENGLISH"))
      (is (not (forms/english? :French)))
      (is (not (forms/english? nil))))
    (testing "default-lang picks default language correctly"
      (let [langs-with-english [:Nepali :French :English]
            langs-no-english [:Nepali :French]]
        (is (= (forms/default-lang langs-no-english) :French))
        (is (= (forms/default-lang langs-with-english) :English))))))

(deftest get-label-extracts-the-label-from-a-labelled-object
  (let [nl1 {:name "foo" :label "My Label"}
        nl2 {:name "bar" :label {:English "My Label" :Nepali "मेरो लेबल"}}
        nl3 {:name "q1" :full-name "group/q1"}]
    (testing "get label extracts label when language is not given."
      (is (= (forms/get-label nl1) "My Label"))
      (is (= (forms/get-label nl2) "My Label"))
      (is (nil?  (forms/get-label "")))
      (is (nil? (forms/get-label {}))))
    (testing "get label extracts label when langauge is given."
      (is (= (forms/get-label nl2 :English) "My Label"))
      (is (= (forms/get-label nl2 :French) "My Label"))
      (is (= (forms/get-label nl2 :Nepali) "मेरो लेबल")))
    (testing "get label gets the name if label doesn't exist."
      (is (= (forms/get-label nl3) "q1")))))

(defn- two-num-array? [coll] (= [true true] (map number? coll)))

(deftest format-answer-formats-answers-according-to-the-field
  (let [form (forms/flatten-form single-language-form)
        q-sel1 (first (filter forms/select-one? form))
        q-selm (first (filter forms/select-all? form))
        q-int (first (filter forms/numeric? form))
        q-text (first (filter forms/text? form))
        q-img (merge q-text {:type "image"})
        q-repeat (merge q-text {:type "repeat"})]
    (testing "select one answers are formatted properly"
      (let [rand-option (rand-nth (:children q-sel1))]
        (is (= (forms/format-answer q-sel1 (:name rand-option)) (:label rand-option)))
        (is (= (forms/format-answer q-sel1 nil) forms/no-answer))))
    (testing "text and integer answers are left alone"
      (is (= (forms/format-answer q-text "foo") "foo"))
      (is (= (forms/format-answer q-text 1) 1))
      (is (= (forms/format-answer q-text "1") "1")))
    (testing "select all answers are formatted properly"
      (let [first-option (first (:children q-selm))
            two-options (take 2 (:children q-selm))
            two-answers (string/join #" " (map :name two-options))
            has-label? #(partial utils/substring? (:label %) %)]
        (is (= (forms/format-answer q-selm "") "No Answer"))
        (is (has-label? first-option (forms/format-answer q-selm (:name first-option))))
        (is (has-label? (first two-options) (forms/format-answer q-selm two-answers)))
        (is (has-label? (second two-options) (forms/format-answer q-selm two-answers)))))
    (testing "image answers are rendered as they should be"
      (let [image1 "bar.jpg"
            image2 {:filename "foo/bar.jpg"
                    :download_url "IMAGE.jpg"
                    :small_download_url "THUMB.jpg"}
            image3 {:filename "foo/bar.jpg"
                    :download_url "IMAGE.jpg"}
            txt-link (forms/format-answer q-img image2 nil true)]
        (is (= (forms/format-answer q-img nil) nil))
        (is (= (forms/format-answer q-img "") ""))
        (is (= (forms/format-answer q-img image1) "bar.jpg"))
        (is (= (forms/format-answer q-img image2) [:a {:href "IMAGE.jpg" :target "_blank"}
                                                   [:img {:width "80px" :src "THUMB.jpg"}]]))
        (is (= (forms/format-answer q-img image3) [:a {:href "IMAGE.jpg" :target "_blank"}
                                                   [:img {:width "80px" :src "IMAGE.jpg"}]]))
        (is (= (subs txt-link 0 36) "<a href='IMAGE.jpg' target='_blank'>"))
        (is (= (subs txt-link (- (count txt-link) 12) (count txt-link)) "bar.jpg </a>"))))
    (testing "repeat answers returns nil if no element, a list starting with :span otherwise"
      (is (= (forms/format-answer q-repeat []) ""))
      (is (= (forms/format-answer q-repeat [{"foo" "bar"}]) "Repeated data with 1 answers.")))))