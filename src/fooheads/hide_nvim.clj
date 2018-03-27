(ns fooheads.hide-nvim
  (:require [clojure.tools.logging :as log])
  (:refer-clojure :exclude [eval]))

(defn eval [code]
  (log/debug "EVALING" code)
  (clojure.core/eval (read-string code)))
