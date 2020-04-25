(ns fooheads.hide-nvim.client2
  (:require
    ;[fooheads.hide-nvim.rpc :as rpc]
    ;[neovim.core :as nvim]))
    [clojure.core.async :as async]
    [clojure.edn :as edn]
    ;[clojure.tools.logging :as log]
    [fooheads.logging :as log]
    [clojure.walk :as walk]
    [msgpack.clojure-extensions]
    [msgpack.core :as msgpack]))


(defn write-data
  "Pack and write data to output-stream"
  [output-stream data]
  (prn "write: " data)
  (msgpack/pack-stream data output-stream)
  (.flush output-stream))

(defn read-data
  "Read and unpack a raw message from input-stream"
  [input-stream]
  (let [data (msgpack/unpack input-stream)]
    (prn "read: " data)
    data))

(defn send-msg [conn fn-name args]
  (let [channel (:channel conn)
        seq-num 1
        msg [0 channel fn-name args]]
    (write-data (:output-stream conn) msg)))

(defn recv-msg [conn]
  (let [response-msg (read-data (:input-stream conn))]
    (prn "response-msg:" response-msg)
    (let [[msg-type msg-id _ msg] response-msg]
          ;[channel data] msg]
      msg)))

(defn call [conn fn-name args]
  (send-msg conn fn-name args)
  (recv-msg conn))

(defn send-notification [conn fn-name args]
  (let [channel (:channel conn)
        seq-num 1
        msg [2 channel fn-name args]]
    (write-data (:output-stream conn) msg)))

(defn nvim-get-current-buf [conn]
  (call conn "nvim_get_current_buf" []))

(defn nvim-get-api-info [conn]
  (let [request-msg [0 1 "nvim_get_api_info" []]
        _ (write-data (:output-stream conn) request-msg)
        response-msg (read-data (:input-stream conn))
        [msg-type msg-id _ msg] response-msg
        [channel api-info] msg]
    [channel

     (let [api-info (walk/postwalk #(if (map-entry? %) [(keyword (first %)) (second %)] %) api-info)
           api-info (remove #(contains? % :deprecated_since) (:functions api-info))]
       api-info)]))



(defn nvim-get-channel [conn]
  (first (nvim-get-api-info conn)))

(defn set-hide-channel-in-vim [conn]
  (let [channel (:channel conn)
        vim-command (format "let g:hide_channel = %d" channel)
        request-msg [0 1 "nvim_command" [vim-command]]]
    (write-data (:output-stream conn) request-msg)
    (read-data (:input-stream conn))))


(defn make-connection
  [host port]
  (let [socket (java.net.Socket. host port)
        istream (.getInputStream socket)
        ostream (.getOutputStream socket)]
    (.setTcpNoDelay socket true)

    {:socket socket
     :input-stream istream
     :output-stream ostream}))

(defonce state (atom {}))

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
       (let [connection (make-connection host port)
             [channel api-info] (nvim-get-api-info connection)]
         (reset! state
                 {:api-info api-info
                  :connection
                  {:input-stream (:input-stream connection)
                   :output-stream (:output-stream connection)
                   :socket (:socket connection)
                   :channel channel}})

         (set-hide-channel-in-vim (:connection @state))))
     (println "Can't find hide port. Can't start properly."))))


(defn stop []
  (println "Client stopping"))

(defn show [state]
  (-> @state (assoc :api-info '...)))

(defn show-api []
  (-> @state :api-info))


(defn eval-func [func args]
  (let [f (resolve (symbol func))]
    (assert f (str "Unable to resolve " func))
    (try
      (apply f args)
      (catch Exception e (log/error e)))))

(defn event-loop [input-stream output-stream]
  (while true
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
            #_(let [res (eval-func func (cons nvim-client args))]
                (log/debug "res:" res)

                (do
                  ;; We really need to respond something, in order to
                  ;; not hang neovim.
                  (let [response-msg [1 channel nil res]
                        packed (msgpack/pack response-msg)]
                    (.write output-stream packed 0 (count packed))
                    (.flush output-stream)
                    (log/debug "<-m " response-msg))))


            #_(if-not (= func ":quit")
                (recur)
                (log/debug "received :quit. Quitting.")))



          1 ; response
          (let []
            (log/debug "  rpcresponse: ")
            (throw (Exception. "Not Yet Implemented.")))

          2 ; notify
          (let [[func args] msg-data]
            (log/debug "msg-data: " msg-data)
            (log/debug "type: " (class msg-data) (class (first msg-data)))
            (log/debug "  rpcnotify: ")
            (log/debug "    func: " func)
            (log/debug "    args: " args)
            (log/debug "    class args: " (class args))
            (log/debug "<-m")

            #_(eval-func func (cons nvim-client args))

            #_(if-not (= func ":quit")
                (do (let [res (eval-func func (cons nvim-client args))]
                      (log/debug "res: " res))
                    (recur))
                (log/info "received :quit. Quitting.")))

          (throw (Exception. (str "Unsupported msg-type: " msg-type))))))))

(defn event-loop [state]
  (while true
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
            #_(let [res (eval-func func (cons nvim-client args))]
                (log/debug "res:" res)

                (do
                  ;; We really need to respond something, in order to
                  ;; not hang neovim.
                  (let [response-msg [1 channel nil res]
                        packed (msgpack/pack response-msg)]
                    (.write output-stream packed 0 (count packed))
                    (.flush output-stream)
                    (log/debug "<-m " response-msg))))


            #_(if-not (= func ":quit")
                (recur)
                (log/debug "received :quit. Quitting.")))



          1 ; response
          (let []
            (log/debug "  rpcresponse: ")
            (throw (Exception. "Not Yet Implemented.")))

          2 ; notify
          (let [[func args] msg-data]
            (log/debug "msg-data: " msg-data)
            (log/debug "type: " (class msg-data) (class (first msg-data)))
            (log/debug "  rpcnotify: ")
            (log/debug "    func: " func)
            (log/debug "    args: " args)
            (log/debug "    class args: " (class args))
            (log/debug "<-m")

            #_(eval-func func (cons nvim-client args))

            #_(if-not (= func ":quit")
                (do (let [res (eval-func func (cons nvim-client args))]
                      (log/debug "res: " res))
                    (recur))
                (log/info "received :quit. Quitting.")))

          (throw (Exception. (str "Unsupported msg-type: " msg-type))))))))



(defn foo
  "This is a docstring

  And it ends here"
  [])

(clojure.edn/read-string "200")
(int? (clojure.edn/read-string "a"))

(comment
  (clojure.repl/doc foo)
  (start)

  (start "localhost" 50433)


  (async/thread-call
    (partial event-loop
             (-> @state :connection :input-stream)
             (-> @state :connection :output-stream)))

  (show state)
  (:api-info @state)
  (log/debug "Hej")
  (set-hide-channel-in-vim (:connection @state) (:channel (:connection @state)))


  (def channel 0)
  (def seq-num 5)
  (def msg [channel seq-num "nvim_get_api_info" []])
  (write-data (:output-stream (:connection @state)) msg)
  (read-data (:input-stream (:connection @state)))

  (def r *1)
  (-> r first :data (msgpack/unpack))
  (map #(msgpack/unpack (:data %)) r)

  (defn nvim-get-current-buf []
    (let [res (call (:connection @state) "nvim_get_current_buf" [])]
      (prn "res" res)
      (msgpack/unpack (:data res))))

  (nvim-get-current-buf)


  (call (:connection @state) "nvim_get_current_buf" [])
  (call (:connection @state) "nvim_get_current_tabpage" [])
  (call (:connection @state) "nvim_get_current_win" [])

  (call (:connection @state) "nvim_get_current_line" [])
  (call (:connection @state) "nvim_list_bufs" [])
  (call (:connection @state) "nvim_command" [":echo 'hej'"])
  (call (:connection @state) "nvim_list_runtime_paths" [])

  (call (:connection @state) "nvim_buf_get_lines" [])

  (set-hide-channel-in-vim (:connection @state) (-> @state :connection :channel))

  (def api-data *1)
  ;; 0 channel
  ;; 1 seq num
  ;; 2 ???
  ;; 3 What is 11?
  ;;

  (def api-info (nvim-get-api-info (:connection @state)))
  (def channel (first api-info))
  (def api (second api-info))


  (->> api-data (take 3))
  (-> api-data (nth 3) (nth 0))
  (-> api-data (nth 3) (nth 1) keys)

  (require '[clojure.repl])
  (clojure.repl/doc map)
  (clojure.repl/pst)

  (require '[neovim.core :as n])
  (n/command ":echo 'Hello, World'")
  (n/win-get-cursor (n/get-current-win))

  (defrecord Buffer [n])
  (defrecord Window [id])
  (defrecord Tabpage [handle])

  (msgpack.macros/extend-msgpack
   Buffer
   0
   [buffer] (msgpack/pack (:n buffer))
   [bytes] (->Buffer (msgpack/unpack bytes)))

  (msgpack/unpack (msgpack/pack {:foo 1}))

  (call (:connection @state) "nvim_get_current_buf" [])
  (call (:connection @state) "nvim_get_current_win" [])
  (call (:connection @state) "nvim_get_current_tabpage" [])

  (def b (call (:connection @state) "nvim_get_current_buf" []))
  (def num_lines (call (:connection @state) "nvim_buf_line_count" [b]))
  (call (:connection @state) "nvim_buf_get_lines" [b 0 (+ 1 num_lines) false])
  (call (:connection @state) "nvim_buf_set_lines" [b 292 292 false ["  ; YES!"]])

  (call (:connection @state) "nvim_subscribe" ["BufEnter"])

  (msgpack/unpack (:data *1))
  (msgpack/unpack (msgpack/pack (->Window 17)))
  (msgpack/unpack (msgpack/pack (->Window 1000)))

  (call (:connection @state) "nvim_win_get_cursor" [1000])
  (type *1))
