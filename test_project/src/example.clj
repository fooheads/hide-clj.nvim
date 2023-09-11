(ns example
  "Example namespace")


(defn add
  "Adds two numbers"
  [a b]
  (+ a b))


(defn sub
  "Subs tow numbers"
  [a b]
  (- a b))


(comment
  (reduce add [1 2 3])
  (map inc [1 2 3]))

