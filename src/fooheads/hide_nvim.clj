(ns fooheads.hide-nvim
  (:require [fooheads.hide :as h] 
            [fooheads.hide-nvim.rpc :as rpc]
            [clojure.main]))

(def eval-code h/eval-code)

(defn -main [& args]
  (rpc/start 7778)
  (clojure.main/repl))



