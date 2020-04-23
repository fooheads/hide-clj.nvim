(ns fooheads.logging
  (:require
    [clojure.string :as str]))

(defn error [& s]
  (println "\n| " (str/join " "s)))

(defn debug [& s]
  (println "\n| " (str/join " " s)))

