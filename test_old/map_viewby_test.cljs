(ns ona.dataview.map-viewby-test
  (:require-macros [cljs.test :refer (is deftest testing)])
  (:require [cljs.test :as t]
            [ona.utils.seq :refer [remove-nil ordered-diff]]
            [ona.dataview.map-viewby :as vb]))

;; VIEW BY HELPER FUNCTION TESTS
(deftest viewby-info-tests
  (let [sel1 {:type "select one" :label "SELECT!"
              :children [{:name "1" :label "Option 1"}
                         {:name "2" :label "Option 2"}]}
        sel1-big {:type "select one" :lable "SELECT 1!"
                  :children (for [i (range 20)]
                              {:name (str i) :label (str "Option " i)})}
        selm (merge sel1 {:type "select all that apply"})
        intf (merge sel1 {:type "integer"})
        N 50
        ids (range N)
        sel1-answers (for [i ids] (rand-nth [nil "1" "2"]))
        sel1-answers-big (map str (range 20))
        selm-answers (for [i ids] (rand-nth [nil "1" "2" "1 2"]))
        int-answers (concat [0 99 nil] (for [i (range (- N 3))] (rand-int 100)))
        num-nil-sel1 (count (filter nil? sel1-answers))
        num-nil-selm (count (filter nil? selm-answers))
        num-nil-int (count (filter nil? int-answers))
        vbi-sel1 (vb/viewby-info sel1 sel1-answers ids)
        vbi-sel1-big (vb/viewby-info sel1-big sel1-answers-big ids)
        vbi-selm (vb/viewby-info selm selm-answers ids)
        vbi-int (vb/viewby-info intf int-answers ids)
        sel1-colors (set (take 3 vb/qualitative-palette))
        selm-colors #{"#f30"}
        int-colors (set vb/sequential-palette)
        ks [:field :answers :id->answers
            :answer->selected? :answer->color :answer->count]
        map-els #{:answer->color :answer->count :answer->selected?}]
    (testing "viewby-info functions return the right types"
      (is (every? (set (keys vbi-sel1)) ks))
      (is (every? (set (keys vbi-selm)) ks))
      (is (every? (set (keys vbi-int)) ks))
      (is (every? #{true}
                  (->> (select-keys vbi-sel1 map-els) vals (map map?))))
      (is (every? #{true}
                  (->> (select-keys vbi-selm map-els) vals (map map?))))
      (is (every? #{true}
                  (->> (select-keys vbi-int map-els) vals (map map?)))))
    (testing "viewby-info works for select one"
      (let [{:keys [answers answer->color answer->count
                    answer->selected?]} vbi-sel1]
        ;; answers
        (is (= (sort answers) (sort (conj (map :name (:children sel1)) nil))))
        (is (= (remove-nil answers)
               (remove-nil (map first (sort-by second > answer->count)))))
        ;; answer->color
        (is (= (sort answers) (sort (keys answer->color))))
        (is (every? sel1-colors (map answer->color answers)))
        (is (every? selm-colors (map (:answer->color vbi-sel1-big)
                                      (:answers vbi-sel1-big))))
        ;; answer->count
        (is (= (sort answers) (sort (keys answer->count))))
        (is (= (count ids) (apply + (vals answer->count))))
        ;; answer->selected?
        (is (= (sort answers) (sort (keys answer->selected?))))
        (is (= false (answer->selected? nil)))
        (is (every? #{true} (map answer->selected? (remove-nil answers))))))
    (testing "id-color-selected? works for select one"
      (let [{:keys [id-color id-selected?]} (vb/id-color-selected vbi-sel1)]
        ;; id-color
        (is (every? sel1-colors (map id-color ids)))
        ;; id-selected?
        (is (= (- N num-nil-sel1) (count (filter id-selected? ids))))))
    (testing "viewby-info works for integer"
      (let [{:keys [answers answer->color answer->count answer->selected?]} vbi-int]
        ;; answers
        (is (= answers ["0 to 19" "20 to 39" "40 to 59" "60 to 79" "80 to 99" nil]))
        ;; answer->color
        (is (= (remove-nil (sort answers)) (sort (keys answer->color))))
        (is (every? (set vb/sequential-palette) (remove-nil (map answer->color answers))))
        ;; answer->count
        (is (= (sort answers) (sort (keys answer->count))))
        (is (= (count ids) (apply + (vals answer->count))))
        ;; answer->selected?
        (is (= (sort answers) (sort (keys answer->selected?))))
        (is (= false (answer->selected? nil)))
        (is (every? #{true} (map answer->selected? (remove-nil answers))))))
    (testing "id-color-selected? works for integer"
      (let [{:keys [id-color id-selected?]} (vb/id-color-selected vbi-int)]
        ;; id-color
        (is (every? int-colors (remove-nil (map id-color ids))))
        ;; id-selected?
        (is (= (- N num-nil-int) (count (filter id-selected? ids))))))
    (testing "viewby-info works for select multiple"
      (let [{:keys [answer->color answer->count answer->selected?
                    answers id-color id-selected?]} vbi-selm
            sorted-answers (sort answers)]
        ;; answers
        (is (= sorted-answers (sort (conj (map :name (:children selm)) nil))))
        (is (= (last answers) nil))
        (is (= (remove-nil answers)
               (remove-nil (map first (sort-by second > answer->count)))))
        ;; answer->color
        (is (= sorted-answers (sort (keys answer->color))))
        (is (every? selm-colors (map answer->color answers)))
        ;; answer->count
        (is (= sorted-answers (sort (keys answer->count))))
        (is (< (count ids) (apply + (vals answer->count))))
        ;; answer->selected?
        (is (= sorted-answers (sort (keys answer->selected?))))
        (is (= false (answer->selected? nil)))
        (is (every? #{true} (map answer->selected? (remove-nil answers))))))
    (testing "id-color-selected? works for select multiple initially"
      (let [id->ans (:id->answers vbi-selm)
            sel (:answer->selected? vbi-selm)
            {:keys [id-color id-selected?]} (vb/id-color-selected vbi-selm)]
        ;; id-color
        (is (every? selm-colors (map id-color ids)))
        ;; id-selected?
        (is (= (- N num-nil-selm) (count (filter id-selected? ids))))))
    (testing "id-color-selected? works for sel-mult when answers deselected"
      (let [sel (:answer->selected? vbi-selm)
            sel (update-in sel ["1"] not) ;; set "1" to be deselected
            new-acc (merge vbi-selm {:answer->selected? sel})
            id->ans (:id->answers new-acc)
            {:keys [id-color id-selected?]} (vb/id-color-selected new-acc)]
        ;; id-color
        (is (every? selm-colors (map id-color ids)))
        ;; id-selected? -- check that all items without "1" are selected
        (is (= (map id-selected? ids)
               (for [id ids :let [ans (id->ans id)]]
                 (if (= nil ans) (get sel ans)
                   (some identity (map sel ans))))))))))

(deftest test-selection-toggle
  (testing "toggle-answer-selected testing"
  (let [initial {:a true :b true :c true nil false}
        a (vb/toggle-answer-selected initial :a)
        a-b (vb/toggle-answer-selected a :b)
        a-b-b (vb/toggle-answer-selected a-b :b)
        a-b-c-nil (-> a-b
                      (vb/toggle-answer-selected :c)
                      (vb/toggle-answer-selected nil))
        all-false (zipmap (keys initial) (repeat false))
        all-true (zipmap (keys initial) (repeat true))]
    (is (= a (merge all-false {:a true})))
    (is (= a-b (merge all-false {:a true :b true})))
    (is (= a-b-b (merge all-false {:a true})))
    (is (= a-b-c-nil initial)))))
