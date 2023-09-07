(ns fooheads.hide-nvim
  (:require
    [clojure.java.io :as io]
    [clojure.main]
    [clojure.stacktrace :refer [print-stack-trace]]
    [fooheads.hide-nvim.client :as client]
    [fooheads.hide-nvim.navigate :as nav]
    [puget.printer :as puget]
    [time-literals.data-readers]
    [time-literals.read-write]))


#_(def eval-code h/eval-code)


(defn eval-code
  [connection code]
  ;(prn "CODE:" args)
  (prn "ns:" (nav/get-namespace connection))
  (prn "code:" code)
  (println))


(defn ns-switch
  [connection & _args]
  (prn "ns:" (nav/get-namespace connection)))


(defn repl-prompt-separator
  []
  (apply str (repeat 20 "-")))


(defn repl-prompt
  []
  (printf "%s\n%s=> " (repl-prompt-separator) (ns-name *ns*)))


(defonce client (atom {}))


(defn repl-init
  []
  (set! *print-length* 10)
  (reset! client (client/start))
  (println "\nWelcome to Hide - The Headless IDE!"))

  ; (binding [*ns* (find-ns 'user)]
  ;   (require '[fooheads.hide-nvim.client :refer [reset]]))



;; see *options* in puget/printer.clj to understand how to control output:
;; https://github.com/greglook/puget/blob/master/src/puget/printer.clj


(def time-handlers
  {java.time.Month (puget/tagged-handler 'time/month str)
   java.time.Period (puget/tagged-handler 'time/period str)
   java.time.LocalDate (puget/tagged-handler 'time/date str)
   java.time.LocalDateTime (puget/tagged-handler 'time/date-time str)
   java.time.ZonedDateTime (puget/tagged-handler 'time/zoned-date-time str)
   java.time.OffsetDateTime (puget/tagged-handler 'time/offset-date-time str)
   java.time.Instant (puget/tagged-handler 'time/instant str)
   java.time.LocalTime (puget/tagged-handler 'time/time str)
   java.time.Duration (puget/tagged-handler 'time/duration str)
   java.time.Year (puget/tagged-handler 'time/year str)
   java.time.YearMonth (puget/tagged-handler 'time/year-month str)
   java.time.ZoneRegion (puget/tagged-handler 'time/zone str)
   java.time.DayOfWeek (puget/tagged-handler 'time/day-of-week str)})


(def other-print-handlers
  {java.util.UUID (puget/tagged-handler 'uuid str)})


(def print-handlers
  (merge time-handlers other-print-handlers))


(defn config
  []
  (let [config-filename (format "%s/.clojure/hide-config.edn"
                                (System/getenv "HOME"))]
    (if (.exists (io/as-file config-filename))
      (read-string (slurp config-filename))
      {})))


(defn pprn
  [& forms]
  (alter-var-root
    #'puget.printer/*options*
    (fn [m]
      (merge m
             {:coll-limit 1000          ;; most collections
              :map-delimiter " "
              :print-color true
              :sort-keys 1000
              :width 80}
             (:print-options (config)))))

  (doseq [form forms]
    (puget/pprint form {:print-handlers print-handlers})))


(defn repl-print
  [form]
  (let [config (config)
        post-hook (get-in config [:repl :post-hook])]
    (println "\nresult:\n")
    (pprn form)

    (when (symbol? post-hook)
      (when-let [f (resolve post-hook)]
        (try
          (f form)
          (catch Exception e
            (print-stack-trace e)))))))


(defn print-log
  []
  (let [log (client/get-log @client)]
    (prn "num entries:" (count log))
    (doseq [msg log]
      (prn msg))))


(defn -main
  [& _args]
  (println "Hello")
  (clojure.main/repl
    :init #'repl-init
    :prompt #'repl-prompt
    :print #'repl-print)

  (client/stop @client)
  (println "Goodbye")
  #_(System/exit 0))


(comment
  (def client (atom (client/start "localhost" 7777)))

  (client/exec @client "nvim_open_win" [0 true {"relative" "editor"
                                                "row" 1
                                                "col" 1
                                                "width" 100
                                                "height" 100}])
  (def w *1)

  (client/exec @client "nvim_win_close" [w false])

  (def b (client/exec @client "nvim_create_buf" [false false]))
  (client/exec @client "nvim_command" ["sp"])
  (client/exec @client "nvim_command" ["wincmd H"])
  (def b (client/exec @client "nvim_create_buf" [true true]))
  (client/exec @client "nvim_command" [(format "buffer %d" (:n b))]))


(comment
  (pprn (java.util.UUID/randomUUID))
  (pprn #time/month "JUNE")
  (pprn #time/period "P1D")
  (pprn #time/date "2039-01-01")
  (pprn #time/date-time "2018-07-25T08:08:44.026")
  (pprn #time/zoned-date-time "2018-07-25T08:09:11.227+01:00[Europe/London]")
  (pprn #time/offset-date-time "2018-07-25T08:11:54.453+01:00")
  (pprn #time/instant "2018-07-25T07:10:05.861Z")
  (pprn #time/time "08:12:13.366")
  (pprn #time/duration "PT1S")
  (pprn #time/year "3030")
  (pprn #time/year-month "3030-01")
  (pprn #time/zone "Europe/London")
  (pprn #time/day-of-week "TUESDAY"))


