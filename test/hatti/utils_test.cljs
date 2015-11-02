(ns hatti.utils-test
  (:require-macros [cljs.test :refer [is deftest testing]])
  (:require [hatti.utils :refer [hyphen->camel-case]]))

(deftest hyphen->camel-case-test
  (testing "converts strings with single hyphen to camel case correctly"
    (is (= (hyphen->camel-case "a-string") "aString")))
  (testing "converts strings with multiple hyphens to camel case correctly"
    (is (= (hyphen->camel-case "a-string-with-lots-of-hyphens") "aStringWithLotsOfHyphens"))))
