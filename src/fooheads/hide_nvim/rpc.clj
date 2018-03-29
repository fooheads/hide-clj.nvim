(ns fooheads.hide-nvim.rpc
  (:require [fooheads.hide-nvim]
            [clojure.core.async :as async] 
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

(defn eval-func [func args]
  (let [f (resolve (symbol func))]
    (try
      (apply f args)
      (catch Exception e (.getMessage e)))))

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
            (let [res (eval-func func args)]
              (log/debug "res:" res)

              (do 
                ;; We really need to respond something, in order to 
                ;; not hang neovim.
                (let [response-msg [1 channel nil res] 
                      packed (msgpack/pack response-msg)]
                  (.write output-stream packed 0 (count packed))     
                  (.flush output-stream)
                  (log/info "<-m " response-msg)
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
            (log/debug "<-m")

            (if-not (= func ":quit") 
              (recur)
              (log/debug "received :quit. Quitting.")))

          (throw (Exception. (str "Unsupported msg-type: " msg-type))))))))

(defn write-message [{:keys [input-stream output-stream]} packed-msg]
    (.write output-stream packed-msg 0 (count packed-msg))     
    (.flush output-stream))

(defn read-message [{:keys [input-stream output-stream]}]
   (msgpack/unpack input-stream))

(defn nvim-get-api-info [conn]
  (let [request-msg [0 1 "nvim_get_api_info" []]
        packed (msgpack/pack request-msg)
        _ (write-message conn packed)
        response-msg (read-message conn)
        [msg-type msg-id _ msg] response-msg
        [channel api-info] msg]

    (log/debug "channel: " channel)
    ; (log/debug "api-info: " (keys api-info))
    channel
    [channel api-info]))

(defn nvim-get-channel [conn]
  (first (nvim-get-api-info conn)))

(defn set-hide-channel-in-vim [conn channel]
  (prn "channel: " channel)
  (prn (class channel))
  (let [vim-command (format "let g:hide_channel = %d" channel)
        request-msg [0 1 "nvim_command" [vim-command]]
        packed (msgpack/pack request-msg)
        _ (write-message conn packed)
        response-msg (read-message conn)
        ]

    (log/debug "response: " response-msg)))

(defn start []
  (let [conn (connect)
        input-stream (:input-stream conn)
        output-stream (:output-stream conn)
        ]

    ;; Call nvim_get_api_info
    ;; Channel is first argument in response

    (prn "channel:" (nvim-get-channel conn))
    (set-hide-channel-in-vim conn (nvim-get-channel conn))

    (async/thread-call (partial event-loop input-stream output-stream))
    ))





