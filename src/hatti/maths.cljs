(ns hatti.maths)

;; JS MATH INTEROP
(defn floor [n] (when n (.floor js/Math n)))
(defn ceil [n]  (when n (.ceil js/Math n)))
(defn round [n] (when n (.round js/Math n)))
(defn abs [n] (when n (.abs js/Math n)))


;; The following largely from the clojure-only clojure.math.numeric-tower.
;; https://github.com/clojure/math.numeric-tower
(defn gcd "(gcd a b) returns the greatest common divisor of a and b" [a b]
  (if (or (not (integer? a)) (not (integer? b)))
    (throw (js/IllegalArgumentException. "gcd requires two integers"))
    (loop [a (abs a) b (abs b)]
      (if (zero? b) a,
	  (recur b (mod a b))))))

(defn lcm
  "(lcm a b) returns the least common multiple of a and b"
  [a b]
  (when (or (not (integer? a)) (not (integer? b)))
    (throw (js/IllegalArgumentException. "lcm requires two integers")))
  (cond (zero? a) 0
        (zero? b) 0
        :else (abs (* b (quot a (gcd a b))))))
