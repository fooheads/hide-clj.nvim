(ns fooheads.hide-nvim.client
  (:require [fooheads.hide-nvim.rpc :as rpc]
            [neovim.core :as nvim]))

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


(defn make-connection
  [host port]
  (let [socket (java.net.Socket. host port)
        istream (.getInputStream socket)
        ostream (.getOutputStream socket)]
    (.setTcpNoDelay socket true)

    {:socket socket
     :input-stream istream
     :output-stream ostream}))


;;(defn make-connection [host port]
;;  {:input-stream :da-input-stream
;;   :output-stream :da-output-stream})

(defn make-event-loop [nvim-client connection]
  ;(future (while true (println "Alive") (Thread/sleep 5000)))
  (rpc/event-loop 
    nvim-client 
    (:input-stream connection)
    (:output-stream connection)))

(defn my-system [host port]
  (println (format "Connecting to %s:%d" host port))
  (fn [do-with-state]
    (with-open [connection (closeable (make-connection host port) (fn [conn] (.close (:socket conn))))
                nvim-client (closeable (nvim/client 1 host port)) 
                channel (rpc/nvim-get-channel @connection)
                _ (rpc/set-hide-channel-in-vim @connection channel)
                event-loop (future (make-event-loop @nvim-client @connection))]

      (do-with-state {:connection @connection
                      :event-loop @event-loop}))))


      

(def port (or (some-> "HIDE_PORT" System/getenv Integer/parseInt)
	      7778))

(def with-my-system (my-system "localhost" port))

(defn await-event-loop [state]
  ; (deref (:event-loop state))
  (println "AWAIT EVENT LOOP DONE"))
  


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

(defn reset 
  ([client] (reset)) 
  ([]
   (stop)
   (clojure.tools.namespace.repl/refresh :after 'fooheads.hide-nvim.client/start)))



; Run
;(def instance (future-call @init))

; (start)
; 
; @state
; 
; (stop)
; 
; 
; ; Stop
; (future-cancel instance)
; @instance
; 
; (def event-loop (make-event-loop {}))
; (deref event-loop)
; (future-cancel event-loop)
; (realized? event-loop)
