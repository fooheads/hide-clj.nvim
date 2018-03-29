(ns fooheads.hide-nvim.client)

(defn make-connection [host port]
  {:input-stream :da-input-stream
   :output-stream :da-output-stream})

(defn make-event-loop [connection]
  (future (while true (println "Alive") (Thread/sleep 5000))))

(defn my-system [host port config-options]
  (fn [do-with-state]
    (with-open [connection (closeable (make-connection host port))
                event-loop (closeable (make-event-loop connection))]

      (do-with-state {:connection @connection
                      :event-loop @event-loop})


      )))

(def with-my-system
  (my-system "localhost" 7777 {:interval "every now and then"}))

(defn await-event-loop [state]
  (deref (:event-loop state)))


;(with-my-system await-event-loop)



(def init (atom #(throw (ex-info "init not set" {}))))

(defn publishing-state [do-with-state target-atom]
  #(do (reset! target-atom %)
       (try (do-with-state %)
            (finally (reset! target-atom nil)))))

(def state (atom nil))


; Configure
(reset! init #(with-my-system (-> await-event-loop
                                  (publishing-state state))))

(def instance (atom (future ::never-run)))


(defn start []
  (swap! instance #(if (realized? %)
                     (future-call @init)
                     (throw (ex-info "already running" {})))))

(defn stop []
  (let [instance-future @instance]
    (future-cancel instance-future)
    (try @instance-future
         (catch java.util.concurrent.CancellationException _ :stopped))))

(defn reset []
  (stop)
  (clojure.tools.namespace.repl/refresh :after 'user/start))


; Run
;(def instance (future-call @init))

(start)

@state

(stop)


; Stop
(future-cancel instance)
@instance

(def event-loop (make-event-loop {}))
(deref event-loop)
(future-cancel event-loop)
(realized? event-loop)
