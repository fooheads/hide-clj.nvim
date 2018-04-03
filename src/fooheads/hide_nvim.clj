(ns fooheads.hide-nvim
  (:require [fooheads.hide :as h] 
            ; [fooheads.hide-nvim.rpc :as rpc]
            [fooheads.hide-nvim.client :as client]
            [clojure.main]
            [puget.printer :as puget]))

(def eval-code h/eval-code)

(def repl-prompt-separator (apply str (repeat 20 "-")))

(defn repl-prompt [] 
  (printf "%s\n%s=> " repl-prompt-separator (ns-name *ns*)))

(defn repl-init []
  (client/start)
  (println "\nWelcome to Hide - The Headless IDE!")
  )

(defn -main [& args]

  (binding [*ns* 'user]
    (def reset 'fooheads.hide-nvim.client/reset))

  (clojure.main/repl 
    :init repl-init
    :prompt repl-prompt
    :print puget/cprint)

  (client/stop)
  (System/exit 0))



