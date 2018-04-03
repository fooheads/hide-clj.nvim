(ns fooheads.hide-nvim.rpc
  (:require [fooheads.hide-nvim :as h]
            [clojure.core.async :as async] 
            [clojure.tools.logging :as log]
            [msgpack.clojure-extensions]
            [msgpack.core :as msgpack]
            [neovim.core :as nvim]
            ))

;; 
;; hide-clj.nvim 
;; 

(defn connect 
  ([] (connect 7777))
  
  ([port]
  (let [host "localhost"
        port port
        socket (java.net.Socket. host port)
        istream (.getInputStream socket)
        ostream (.getOutputStream socket)]
    (.setTcpNoDelay socket true)

    {:input-stream istream
     :output-stream ostream})))

(defn start-repl []
  (clojure.core.server/start-server 
    {:address "localhost" :port 8888 :name "hide-neovim-repl" :accept 'clojure.core.server/repl})
  )

(defn eval-func [func args]
  (let [f (resolve (symbol func))]
    (try
      (apply f args)
      (catch Exception e (log/error e)))))

(defn event-loop [nvim-client input-stream output-stream]
  (prn "Started event-loop...")
  (loop []
    (log/debug "waiting for message...")

    (when-let [msg (msgpack/unpack input-stream)]
      (log/debug "->m " msg)
      (let [[msg-type & msg-data] msg]
        ; (log/debug "  msg-type:" msg-type)
        (log/debug "  msg-data:" msg-data)

        (case msg-type 
          0 ; request
          (let [[channel func args] msg-data]

            (throw (Exception. "Unsupported. Only notify is supported.")) 
            (log/debug "  rpcrequest: ")
            (log/debug "    channel: " channel)
            (log/debug "    func: " func)
            (log/debug "    args: " args)
            (log/debug "    class args: " (class args))
            (let [res (eval-func func (cons nvim-client args))]
              (log/debug "res:" res)

              (do 
                ;; We really need to respond something, in order to 
                ;; not hang neovim.
                (let [response-msg [1 channel nil res] 
                      packed (msgpack/pack response-msg)]
                  (.write output-stream packed 0 (count packed))     
                  (.flush output-stream)
                  (log/debug "<-m " response-msg)
                  )))

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
            (log/debug "    class args: " (class args))
            (log/debug "<-m")

            (if-not (= func ":quit") 
              (do (let [res (eval-func func (cons nvim-client args))]
                    (log/debug "res: " res))
                  (recur))
              (log/info "received :quit. Quitting.")))

          (throw (Exception. (str "Unsupported msg-type: " msg-type))))))))

(defn write-message [{:keys [output-stream]} msg]
  (msgpack/pack-stream msg output-stream)
  (.flush output-stream))

(defn read-message [{:keys [input-stream]}]
  (msgpack/unpack input-stream))

(defn send-msg [conn fn-name args]
  (let [channel (:channel conn)
        seq-num 1
        msg [0 channel fn-name args]]
    (write-message conn msg)))

(defn recv-msg [conn]
  (let [response-msg (read-message conn)]
    (let [[msg-type msg-id _ msg] response-msg
          [channel data] msg]
      msg)))

(defn send-recv-msg [conn fn-name args]
  (send-msg conn fn-name args)
  (recv-msg conn))

(defn send-notification [conn fn-name args]
  (let [channel (:channel conn)
        seq-num 1
        msg [2 channel fn-name args]]
    (write-message conn msg)))

(defn nvim-get-current-buf [conn]
  (send-recv-msg conn "nvim_get_current_buf" []))

(defn nvim-get-api-info [conn]
  (let [request-msg [0 1 "nvim_get_api_info" []]
        _ (write-message conn request-msg)
        response-msg (read-message conn)
        [msg-type msg-id _ msg] response-msg
        [channel api-info] msg]
    [channel api-info]))

(defn nvim-get-channel [conn]
  (first (nvim-get-api-info conn)))

(defn set-hide-channel-in-vim [conn channel]
  (let [vim-command (format "let g:hide_channel = %d" channel)
        request-msg [0 1 "nvim_command" [vim-command]]]
    (write-message conn request-msg)
    (read-message conn)))

(defonce state (atom {}))

(defn conn [] (:conn @state))

(defn start 
  ([] (start 7777))
  ([port] (start "localhost" port))
  ([host port]
   (log/infof "Connecting to neovim at port %s:%d" host port)

   (let [conn (connect port)
         nvim-client (nvim/client 1 host port) 
         input-stream (:input-stream conn)
         output-stream (:output-stream conn)
         ]

     ;; Call nvim_get_api_info
     ;; Channel is first argument in response

     (let [channel (nvim-get-channel conn)]
       (set-hide-channel-in-vim conn channel)
       (reset! state {:conn (assoc conn :channel channel)}))

     (async/thread-call (partial event-loop nvim-client input-stream output-stream))
     )))





