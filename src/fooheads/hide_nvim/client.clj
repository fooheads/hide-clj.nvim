(ns fooheads.hide-nvim.client
  (:require
    [clojure.core.async :as async]
    [clojure.edn :as edn]
    [fooheads.hide-nvim.commands :as commands]
    [fooheads.hide-nvim.connection :as c]))


(defn execute-command
  [connection method params]
  ;(prn "execute..." method params)
  (if-let [f (get commands/command-map (keyword method))]
    (do
      ;(prn "found f: " f)
      (f connection))
    (println (format "Can't find command '%s'" method))))


(defn- event-loop
  [channel event-connection command-connection]
  (let [quit? (atom false)]
    (async/go
      (while (not @quit?)
        (Thread/sleep 100)

        (try
          (if-let [msg (async/poll! channel)]
            (if (= "quit" msg)
              (reset! quit? true)))

          (if (c/data-available? event-connection)
            (let [msg (c/receive-message-blocking event-connection)]
              ;(prn "event-loop: msg" msg)
              (case (:type msg)
                :notification-msg
                (try
                  ;(prn "event-loop: " "func" (:method msg) "args" (:params msg))
                  (execute-command command-connection (:method msg) (:params msg))
                  (catch Exception e
                    (c/log-msg command-connection {:ex-msg (.getMessage e) :ex e})))

                (throw (Exception. (str "Unsupported msg-type: " (:type msg)))))))

          (catch Throwable e
            (c/log-msg event-connection {:ex-msg (.getMessage e) :ex e})
            (def *ex e)
            (println "Exception in hide. Exception stored in fooheads.hide-nvim.client/*ex for inspection")
            (println "Exception message:" (.getMessage e)))))
            ;(reset! quit? true))))
      (async/close! channel)
      (println "Quitting - exiting event-loop!"))))


(defn exec
  [client method params]
  (let [connection (:command-connection @client)]
    (c/call connection method params)))


(defn set-hide-channel-in-vim
  [connection hide-channel]
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

         (event-loop event-channel event-connection command-connection)
         (set-hide-channel-in-vim command-connection vim-hide-channel)

         {:command-connection command-connection
          :event-connection event-connection
          :event-channel event-channel}))
     (println "Can't find hide port. Can't start properly."))))


(defn stop
  [client]
  (async/>!! (:event-channel client) "quit"))


(defn- log-entries
  [conn-name connection]
  (map (fn [log-entry] (merge {:conn conn-name} log-entry)) (:log connection)))


(defn get-log
  [client]
  (let [ts-asc-conn-desc
        (fn [[a1 b1] [a2 b2]] (if (= a1 a2) (compare b2 b1) (compare a1 a2)))

        command-log (log-entries :command (deref (:command-connection client)))
        event-log (log-entries :event (deref (:event-connection client)))]
    (sort-by
      (juxt :ts :conn)
      ts-asc-conn-desc
      (concat command-log event-log))))


(comment
  (def client (start "localhost" 7777))
  (def client (start "localhost" 50232))

  (exec client "nvim_get_current_line" [])
  (exec client "nvim_command" [":echo 'testing2'"])

  (stop client))


