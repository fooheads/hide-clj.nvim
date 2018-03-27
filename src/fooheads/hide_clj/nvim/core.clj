(ns fooheads.hide-clj.nvim.core
  (:require [clojure.core.async :as async] 
            [clojure.tools.logging :as log]
            [msgpack.clojure-extensions]
            [msgpack.core :as msgpack]
            ))

;; 
;; hide-clj.nvim 
;; 

(defn connect []
  (let [host "localhost"
        port 7777
        socket (java.net.Socket. host port)
        istream (.getInputStream socket)
        ostream (.getOutputStream socket)]
    (.setTcpNoDelay socket true)

    {:input-stream istream
     :output-stream ostream}))

(defn start-repl []
  (clojure.core.server/start-server 
    {:address "localhost" :port 8888 :name "hide-neovim-repl" :accept 'clojure.core.server/repl})
  )

(defn event-loop [input-stream output-stream]
  (loop []
    (log/info "waiting for message...")

    (when-let [msg (msgpack/unpack input-stream)]
      (log/info "->m " msg)
      (let [[msg-type & msg-data] msg]
        ; (log/debug "  msg-type:" msg-type)
        (log/debug "  msg-data:" msg-data)

        (case msg-type 
          0 ; request
          (let [[channel func args] msg-data]
            (log/debug "  rpcrequest: ")
            (log/debug "    channel: " channel)
            (log/debug "    func: " func)
            (log/debug "    args: " args)

            (do 
              ;; We really need to respond something, in order to 
              ;; not hang neovim.
              (let [response-msg [1 channel nil (str "hej! på! dig! " func)] 
                    packed (msgpack/pack response-msg)]
                (.write output-stream packed 0 (count packed))     
                (.flush output-stream)
                (log/info "<-m " response-msg)
                ))

            (if-not (= func ":quit") 
              (recur)
              (log/debug "received :quit. Quitting.")))

          1 ; response
          (let []
            (log/debug "  rpcresponse: ")
            (throw (Exception. "Not Yet Implemented.")))

          2 ; notify
          (let [[func args] msg-data]
            (log/debug "type: " (class msg-data) (class (first msg-data)))
            (log/debug "  rpcnotify: ")
            (log/debug "    func: " func)
            (log/debug "    args: " args)
            (log/debug "<-m")

            (if-not (= func ":quit") 
              (recur)
              (log/debug "received :quit. Quitting.")))

          (throw (Exception. (str "Unsupported msg-type: " msg-type))))))))

(defn start []
  (let [conn (connect)
        input-stream (:input-stream conn)
        output-stream (:output-stream conn)]
    (async/thread-call (partial event-loop input-stream output-stream))))


