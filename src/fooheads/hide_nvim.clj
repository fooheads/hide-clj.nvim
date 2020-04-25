(ns fooheads.hide-nvim
  (:require ;[fooheads.hide :as h]
            ; [fooheads.hide-nvim.rpc :as rpc]
            [fooheads.hide-nvim.client2 :as client]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.main]
            [clojure.pprint :refer [pprint]]
            [puget.printer :as puget]))

#_(def eval-code h/eval-code)

(defn repl-prompt-separator [] (apply str (repeat 20 "-")))

(defn repl-prompt []
  (printf "%s\n%s=> " (repl-prompt-separator) (ns-name *ns*)))

(defn repl-init []
  (set! *print-length* 10)
  #_(client/start)
  #_(binding [*ns* (find-ns 'user)]
      (require '[fooheads.hide-nvim.client :refer [reset]]))

  (println "\nWelcome to Hide - The Headless IDE!"))


;; see *options* in puget/printer.clj to understand how to control output:
;; https://github.com/greglook/puget/blob/master/src/puget/printer.clj

(defn hide-print [form]
  (let [config-filename (format "%s/.clojure/hide-config.edn"
                                (System/getenv "HOME"))
        config (if (.exists (io/as-file config-filename)) (read-string (slurp config-filename)) {})]

    (alter-var-root
      #'puget.printer/*options*
      (fn [m]
        (merge m
               {:coll-limit 1000          ;; most collections
                :map-delimiter " "
                :print-color true
                :sort-keys 1000
                :width 80}
               (:print-options config))))

    (println "\nresult:\n")
    (puget/pprint form)))

(defn -main [& args]
  (println "Hello")
  (clojure.main/repl
    :init #'repl-init
    :prompt #'repl-prompt
    :print #'hide-print)

  (client/stop)
  (println "Goodbye")
  #_(System/exit 0))



