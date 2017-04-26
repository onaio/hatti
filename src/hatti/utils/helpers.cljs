(ns hatti.utils.helpers)

(defn uses-search-expression?
    "Checks if the appearance-value has the search expression"
    [appearance-value]
    (and (string? appearance-value)
         (re-matches #"^search\(.*\)$" appearance-value)))