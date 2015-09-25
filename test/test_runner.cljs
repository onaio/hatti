(ns test-runner
  (:require
   [cljs.test :as test :refer-macros [run-tests] :refer [report]]
   [hatti.test-utils]
   [hatti.map.utils-test]
   [hatti.map.viewby-test]
   [hatti.ona.forms-test]
   [hatti.ona.post-process-test]
   [hatti.views.chart-test]
   [hatti.views.dataview-test]
   [hatti.views.map-test]
   [hatti.views.record-test]
   [hatti.views.table-test]
   [hatti.shared-test]))

(enable-console-print!)

(defmethod report [::test/default :summary] [m]
  (println "\nRan" (:test m) "tests containing"
           (+ (:pass m) (:fail m) (:error m)) "assertions.")
  (println (:fail m) "failures," (:error m) "errors.")
  (aset js/window "test-failures" (+ (:fail m) (:error m))))

(defn runner []
  (if (cljs.test/successful?
       (run-tests
        (test/empty-env ::test/default)
        'hatti.test-utils
        'hatti.map.utils-test
        'hatti.map.viewby-test
        'hatti.ona.forms-test
        'hatti.ona.post-process-test
        'hatti.views.chart-test
        'hatti.views.dataview-test
        'hatti.views.map-test
        'hatti.views.record-test
        'hatti.views.table-test
        'hatti.shared-test))
   0
   1))
