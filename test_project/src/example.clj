(ns example)


(defn add
  [a b]
  (+ a b))


(defn sub
  [a b]
  (- a b))


(comment
  (reduce add [1 2 3])
  (map inc [1 2 3]))
