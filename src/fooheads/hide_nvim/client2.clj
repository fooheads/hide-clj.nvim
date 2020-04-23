(ns fooheads.hide-nvim.client2
  (:require 
    ;[fooheads.hide-nvim.rpc :as rpc]
    ;[neovim.core :as nvim]))
    [clojure.core.async :as async]    
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
    (let [[msg-type msg-id _ msg] response-msg
          [channel data] msg]
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

(defn set-hide-channel-in-vim [conn channel]
  (let [vim-command (format "let g:hide_channel = %d" channel)
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

(defn start []
  (let [host "localhost"
        port (read-string (System/getenv "HIDE_PORT"))]
    (println "Client starting")
    (swap! state assoc :connection (make-connection host port))
    (let [[channel api-info] (nvim-get-api-info (:connection @state))]
      (swap! state assoc-in [:connection :channel] channel)
      (swap! state assoc :api-info api-info))))
    

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


(comment
  (async/thread-call 
    (partial event-loop 
             (-> @state :connection :input-stream)
             (-> @state :connection :output-stream)))

  (show state)
  (:api-info @state)
  (log/debug "Hej")


  (def channel 0)
  (def seq-num 5)
  (def msg [channel seq-num "nvim_get_api_info" []])
  (write-data (:output-stream (:connection @state)) msg) 
  (read-data (:input-stream (:connection @state)))
  

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
  (clojure.repl/pst))
 
  
