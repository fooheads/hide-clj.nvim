(ns fooheads.system)

(defn closeable
  ([value] (closeable value identity))
  ([value close] 
   (reify
     clojure.lang.IDeref
     (deref [_] value)
     java.io.Closeable
     (close [_] (if (future? value)
                  (future-cancel value)
                  (close value))))))



