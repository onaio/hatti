(ns hatti.utils.submission-review
  (:require [hatti.constants :refer [_review_status]]))

(def approved-text "Approved")
(def approved-status "1")
(def rejected-text "Rejected")
(def rejected-status "2")
(def pending-text "Pending")
(def pending-status "3")

(def review-status-map
  "This hash-map contains the supported statuses that a form submission can be
   assigned."
  {approved-status approved-text
   rejected-status rejected-text
   pending-status pending-text})

(def review-status-list
  [{:review-status approved-status :review-text approved-text}
   {:review-status rejected-status :review-text rejected-text}
   {:review-status pending-status :review-text pending-text}])

(defn get-submission-review-text
  [row-map]
  (get review-status-map
       (if-let [r-status (get row-map _review_status)]
         r-status
         pending-status)))