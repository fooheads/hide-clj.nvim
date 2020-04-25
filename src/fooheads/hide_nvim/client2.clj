(ns fooheads.hide-nvim.client2
  (:require
    ;[fooheads.hide-nvim.rpc :as rpc]
    ;[neovim.core :as nvim]))
    [clojure.core.async :as async]
    [clojure.edn :as edn]
    ;[clojure.tools.logging :as log]
    [fooheads.logging :as log]
    [fooheads.hide-nvim.rpc :as rpc]
    [fooheads.hide-nvim.navigate]
    [clojure.walk :as walk]
    [msgpack.clojure-extensions]
    [msgpack.core :as msgpack]))

(defn write-data
  "Pack and write data to output-stream"
  [output-stream data]
  ;(prn "write: " data)
  (msgpack/pack-stream data output-stream)
  (.flush output-stream))

(defn read-data
  "Read and unpack a raw message from input-stream"
  [input-stream]
  (let [data (msgpack/unpack input-stream)]
    ;(prn "read: " data)
    data))

(defn send-msg [conn fn-name args]
  (let [channel (:channel conn)
        seq-num 1
        msg [0 channel fn-name args]]
    (write-data (:output-stream conn) msg)))

(defn send-msg' [connection [fn-name args]]
  (prn "connection" connection "fn-name" fn-name "args" args)
  (let [channel (:channel @connection)
        seq-num (:seq-num @connection)
        msg [seq-num channel fn-name args]]
    (write-data (:output-stream @connection) msg)
    (swap! connection
           #(-> %
                (update :seq-num inc)
                (update :messages conj msg)))))


(defn send-request
  ([conn msg]
   (send-request conn (:channel @conn) msg))

  ([conn channel [fn-name args]]
   (prn "send-msg'" "fn-name" fn-name "args" args)
   (let [seq-num (:seq-num @conn)
         msg [rpc/msgtype-request channel fn-name args]]
     (swap! conn
            #(-> %
                 (update :seq-num inc)
                 (update :messages conj msg)))
     (write-data (:output-stream @conn) msg))))

;(defn recv-msg [conn]
;  (let [response-msg (read-data (:input-stream conn))]
;    (prn "response-msg:" response-msg)
;    (let [[msg-type msg-id _ msg] response-msg]
;          ;[channel data] msg]
;      msg)))
;
;(defn recv-response [conn]
;  (let [response-msg (read-data (:input-stream @conn))]
;    (prn "response-msg:" response-msg)
;    (let [[msg-type msg-id _ msg] response-msg]
;      msg)))
;
;(defn call [conn msg]
;  (send-msg conn msg)
;  (recv-msg conn))
;
;(defn call'
;  ([conn msg]
;   (call' conn (:channel @conn) msg))
;  ([conn channel msg]
;   (send-request conn msg)
;   (recv-response conn)))

(defn send-notification [conn fn-name args]
  (let [channel (:channel conn)
        seq-num 1
        msg [2 channel fn-name args]]
    (write-data (:output-stream conn) msg)))

#_(defn nvim-get-current-buf [conn]
    (call conn "nvim_get_current_buf" []))

(defn set-hide-channel-in-vim [connection]
  (let [channel (:channel @connection)
        vim-command (format "let g:hide_channel = %d" channel)
        request-msg [0 1 "nvim_command" [vim-command]]]
    (write-data (:output-stream @connection) request-msg)
    (read-data (:input-stream @connection))))


(defn make-connection
  [host port]
  (let [socket (java.net.Socket. host port)
        istream (.getInputStream socket)
        ostream (.getOutputStream socket)]
    (.setTcpNoDelay socket true)

    {:socket socket
     :input-stream istream
     :output-stream ostream}))


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

(defonce connection (atom {}))

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
       (let [conn (make-connection host port)
             [channel api-info] (nvim-get-api-info conn)]
         (reset! connection
                 {:api-info api-info
                  :input-stream (:input-stream conn)
                  :output-stream (:output-stream conn)
                  :socket (:socket conn)
                  :channel channel
                  :seq-num 1
                  :messages []})


         (set-hide-channel-in-vim connection)))
     (println "Can't find hide port. Can't start properly."))))


(defn stop []
  (println "Client stopping"))

(defn show [state]
  (-> @connection (assoc :api-info '...)))

(defn show [state]
  (let [m @connection]
    (if (contains? m :api-info)
      (update m :api-info '...)
      m)))


(defn eval-func [func args]
  (let [f (resolve (symbol func))]
    (assert f (str "Unable to resolve " func))
    (try
      (apply f args)
      (catch Exception e (log/error e)))))

(defn event-loop [channel connection]
  (let [quit? (atom false)]
    (async/go
      (while (not @quit?)
        (Thread/sleep 100)
        ;(log/debug "waiting for message...")

        (if-let [msg (async/poll! channel)]
          (if (= "quit" msg)
            (do
              (println "QUITTING!!!")
              (reset! quit? true))
              ;(async/>! channel "QUITTING!"))
            (do
              (prn "FROM HIDE: " msg)
              (println "Sending to nvim")
              ;(send-msg' connection ["nvim_get_current_line" []])
              ;(send-msg' connection "nvim_get_current_line" [])
              (rpc/call connection msg)
              (println "Done sending to nvim"))))
              ;(async/>! channel ""))))

        (if (> (.available (:input-stream @connection)) 0)
          (when-let [msg (msgpack/unpack (:input-stream @connection))]
            (swap! connection update :messages conj msg)
            ;(log/debug "->m " msg)

            (let [[msg-type & msg-data] msg]
              ; (log/debug "  msg-type:" msg-type)
              ; (log/debug "  msg-data:" msg-data)

              (case msg-type

                msgtype-request
                (let [[channel func args] msg-data]
                  (throw (Exception. "Unsupported. Only notify is supported.")))

                msgtype-response
                (let []
                  (log/debug "  rpcresponse: ")
                  (throw (Exception. "Not Yet Implemented.")))

                2 ; rpc/msgtype-notify
                (let [[func args] msg-data]

                  ;(prn "NOTIFIED: " "func" func "args" args "msg-data" msg-data)
                  (eval-func func (cons connection args))

                  #_(if-not (= func ":quit")
                      (do (let [res (eval-func func (cons nvim-client args))]
                            (log/debug "res: " res))
                          (recur))
                      (log/info "received :quit. Quitting.")))

                (throw (Exception. (str "Unsupported msg-type: " msg-type))))))))
          ;(println "Nothing available"))))
      (println "Exiting event-loop!"))))



(defn foo
  "This is a docstring

  And it ends here"
  [])

(clojure.edn/read-string "200")
(int? (clojure.edn/read-string "a"))

(comment
  (start "localhost" 50433)
  (show connection)

  (def ch (async/thread-call
           (partial event-loop connection)))


  #_(async/go
      (while true
        (println "...")
        (let [s (async/<! echo-chan)]
          (async/>! echo-chan (count s)))))


  (def echo-chan (async/chan))
  (event-loop echo-chan connection)

  (def message ["nvim_get_current_line" []])
  (def message "quit")
  (do
    (async/>!! echo-chan message)
    (async/<!! echo-chan))


  ;; STARTING POINT
  (async/>!! echo-chan "quit")
  (start "localhost" 50433)
  (start "localhost" 52243)

  (def echo-chan (async/chan))
  (event-loop echo-chan connection)

  (do
    (show connection)
    (let [message ["nvim_get_current_buf" []]]
          ;message ["nvim_get_current_line" []]
          ;message ["nvim_command" [":echo 'hej minsann!'"]]
          ;message "quit"])
      (do
        (async/>!! echo-chan message))
      (show connection)))

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

  (call @connection "nvim_get_current_buf" [])
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
  (type *1)

  (def file-path "file:/Users/nicke/.m2/repository/org/clojure/clojure/1.10.1/clojure-1.10.1.jar!/clojure/core.clj")
  (def uri (new java.net.URI file-path))
  (uri? uri)
  (.getPath uri)
  (.toURL uri)
  (re-matches #"(file:.*\.jar)!(.*)" file-path))


