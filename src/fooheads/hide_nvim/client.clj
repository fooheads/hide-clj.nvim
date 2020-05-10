(ns fooheads.hide-nvim.client
  (:require
    [clojure.core.async :as async]
    [clojure.edn :as edn]
    [fooheads.hide-nvim.connection :as c]))

(defn- event-loop [channel connection]
  (let [quit? (atom false)]
    (async/go
      (while (not @quit?)
        (Thread/sleep 100)

        (try
          (if-let [msg (async/poll! channel)]
            (if (= "quit" msg)
              (reset! quit? true)))

          (if (c/data-available? connection)
            (let [msg (c/receive-message-blocking connection)]
              (prn "msg" msg)
              (case (:type msg)
                :notification-msg
                (prn "func" (:method msg) "args" (:params msg))
                (throw (Exception. (str "Unsupported msg-type: " (:type msg)))))))

          (catch Throwable e
            (println "EXCEPTION!" e)
            (prn (ex-data e))
            (reset! quit? true))))
            ;(println "Nothing available"))))
      (async/close! channel)
      (println "Quitting - exiting event-loop!"))))

(defn exec [client method params]
  (let [connection (:command-connection @client)]
    (c/call connection method params)))

(defn set-hide-channel-in-vim [connection hide-channel]
  (let [vim-command (format "let g:hide_channel = %d" hide-channel)]
    (c/call connection "nvim_command" [vim-command])))

(defn start
  ([]
   (let [host "localhost"
         port-str (or (System/getenv "HIDE_PORT")
                      (try (slurp ".hide-port") (catch Exception e nil)))
         port (edn/read-string port-str)]
     (if port
       (start host port)
       (println "Can't find hide port in env HIDE_PORT or in file .hide-port. Can't start properly."))))

  ([host port]
   (if port
     (do
       (println "Client starting")
       (let [command-connection (c/create-connection host port)
             event-connection (c/create-connection host port)
             event-channel (async/chan)
             vim-hide-channel (:channel @event-connection)]

         (event-loop event-channel event-connection)
         (set-hide-channel-in-vim command-connection vim-hide-channel)

         (atom
           {:command-connection command-connection
            :event-connection event-connection
            :event-channel event-channel})))
     (println "Can't find hide port. Can't start properly."))))

(defn stop
  [client]
  (async/>!! (:event-channel @client) "quit"))

(comment
  (def client (start "localhost" 7777))

  (exec client "nvim_get_current_line" [])
  (exec client "nvim_command" [":echo 'testing2'"])

  (stop client))

  ;; TODO: next steps
  ;; 
  ;; - commit
  ;; - map vim notifications to commands/functions
  ;; - queue? or execute on the event-log thread?
  ;; - client message log
  ;; - cleanup all unused code and dependencies
  ;; - move config / vim mappings to hide config
  ;; - make sure puget colors and stuff gets used
  ;;
  ;; - K - docs
  ;; - gd - goto definition
  ;; - evaluate top form inside a (comment)
  ;; 
  ;; - document keyboard commands in README
  ;;
  ;; - repl window
  ;; - send evals through hide.nvim





