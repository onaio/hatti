(ns hatti.views.table-test
  (:require-macros [cljs.test :refer (is deftest testing)]
                   [dommy.core :refer [sel sel1]])
  (:require [chimera.core :refer [not-nil?]]
            [chimera.om.state :refer [merge-into-app-state!]]
            [chimera.urls :refer [last-url-param url]]
            [cljs.core.async :refer [<! chan sliding-buffer put! close!]]
            [cljs.test :as t]
            [dommy.core :as dommy]
            [clojure.string :as string]
            [hatti.shared :as shared]
            [hatti.views :refer [table-page]]
            [hatti.views.table :as tv]
            [hatti.test-utils :refer [new-container! texts owner readonly]]
            [om.core :as om :include-macros true]
            [hatti.shared-test :refer [thin-form small-thin-data no-data
                                       fat-form small-fat-data]]))

;; SLICKGRID HELPER TESTS

(deftest slickgrid-helpers-work
  (let [flat-form (-> fat-form)
        slickgrid-cols (-> flat-form tv/all-fields tv/flat-form->sg-columns
                           (js->clj :keywordize-keys true))
        first-col (first slickgrid-cols)]

    (testing "slickgrid columns have the right types"
      (doseq [col (rest slickgrid-cols)]
        (is (every? #{:id :name :field :sortable :toolTip :type :formatter
                      :headerCssClass :minWidth :cssClass :hxl}
                    (set (keys col))))
        (not-nil? (re-matches (re-pattern (:toolTip col)) (:name col)))))

    (testing "Has the HXL row"
      (doseq [col (rest slickgrid-cols)]
        (is (every? #{:id :name :field :sortable :toolTip :type :formatter
                      :headerCssClass :minWidth :cssClass :hxl}
                    (set (keys col))))
        (not-nil? (re-matches (re-pattern (str (:hxl col))) (:name col)))))

    (testing "slickgrid action column has the right types"
      (is (every? #{:id :name :field :sortable :toolTip :type :formatter
                    :headerCssClass :maxWidth :cssClass}
                  (set (keys first-col)))

          (is (= (:name first-col) (:toolTip first-col)))))
    (testing "compfn works on submission-time"
      (let [submission-col (->> slickgrid-cols
                                (filter #(= (:id %) "_submission_time"))
                                first)
            args (clj->js {:sortCol submission-col})
            nil-item (clj->js {"_submission_time" nil})
            sortfn (tv/compfn args)]
        (doseq [[a b] (partition 2 (shuffle small-fat-data))]
          (is (= 1 (sortfn nil-item (clj->js a))))
          (is (= (if (> (a "_submission_time") (b "_submission_time")) 1 -1)
                 (sortfn (clj->js a) (clj->js b)))))))
    (testing "filterfn basically works"
      ;; in our test data, if 2012-0 exists, it exists for key _submission_time
      (doseq [datum small-fat-data]
        ;; not not turns truthy/falsey into true/false
        (is (= (re-find #"2012-0" (datum "_submission_time"))
               (first
                (tv/filterfn
                 fat-form (clj->js datum) (clj->js {:query "2012-0"})))))))
    (testing "filterfn searches on labels, not names"
      (let [form [{:full-name "q1" :name "q1" :label "Question 1"
                   :type "select one"
                   :children [{:name "o1" :label "Option 1"}
                              {:name "o2" :label "Option 2"}]}]
            data [{"q1" "o1"} {"q1" "o2"}]]
        (is (tv/filterfn
             form (clj->js (first data)) (clj->js {:query "option 1"})))
        (is (tv/filterfn
             form (clj->js (second data)) (clj->js {:query "option 2"})))
        (is (not (tv/filterfn
                  form (clj->js (first data)) (clj->js {:query "o1"}))))))))

(deftest all-fields-test
  (let [fields (tv/all-fields fat-form)
        more-fields (tv/all-fields (concat
                                    [(merge (first fat-form) {:type "note"})
                                     (merge (second fat-form) {:type "group"})]
                                    fat-form))]
    (testing "submission time is always added to fields"
      (is (some #(= "_submission_time" (:name %)) fields))
      (is (some #(= "_submission_time" (:name %)) more-fields)))

    (testing "fields are not repeated"
      (let [all-fields (tv/all-fields fat-form)]
        (is (= (count all-fields) (count (group-by #(:name %) all-fields))))))

    (testing "group and note fields are removed"
      (is (not (->> more-fields (map :type) (contains? #{"note" "group"})))))))

;; TABLE COMPONENT TESTS
(defn- table-container
  [data form role]
  "Returns a container in which a table has been rendered."
  (merge-into-app-state! shared/app-state {:dataset-info {:num_of_submissions 0}
                                           :views {:active [:table]}})
  (shared/update-app-data! shared/app-state data)
  (let [c (new-container!)
        args {:shared {:flat-form form
                       :external-events (chan)
                       :event-chan (chan (sliding-buffer 1))}
              :opts {:role role}}]
    (om/root table-page shared/app-state (merge args {:target c}))
    c))

(deftest table-renders-properly
  (let [table (table-container small-thin-data thin-form owner)
        ;; get title attributes and texts out of the header row
        ths #(sel (sel1 % :.slick-header-columns) :.slick-header-column)
        htexts (fn [table] (->> table ths (map dommy/text)))
        htitles (fn [table] (->> table ths (map #(dommy/attr % :title))))
        edit-urls (fn [d]
                    (let [{:keys [owner project formid]}
                          (:dataset-info @shared/app-state)
                          form-owner (last-url-param owner)
                          project-id (last-url-param project)
                          edit-link (url form-owner project-id formid
                                         (str "webform?instance-id="
                                              (get d "_id")))]
                      edit-link))]

    (testing "empty table shows 'No data'"
      (let [empty-table (table-container no-data thin-form owner)]
        (is (= () no-data))
        (is (not= (.indexOf (dommy/text empty-table) "No data") -1))))

    (testing "all questions on thin tables are rendered"
      (is (every? (->> table htexts set)
                  (list
                   (string/join
                    (concat (->> thin-form (map :label))
                            (->> thin-form (map :instance) (map :hxl))))))))

    (testing "all table headers contain title attributes"
      (not-nil? (re-matches (re-pattern  (string/join (htitles table)))
                            (string/join (htexts table)))))

    (testing "actions column is rendered"
      (is (= (-> (sel table :.record-actions) count dec)
             (count small-thin-data)))
      ;; view submission icons are rendered with correct data-id
      (is (every? (set (map #(get % "_id") small-thin-data))
                  (map #(int (dommy/attr % :data-id))
                       (sel table [:.record-actions :.view-record :i]))))
      ;; edit submission urls are rendered correctly
      (is (every? (set (map edit-urls small-thin-data))
                  (map #(dommy/attr % :href)
                       (sel table [:.record-actions :.edit-record])))))))
