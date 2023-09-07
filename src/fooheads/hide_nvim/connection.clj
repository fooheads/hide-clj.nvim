(ns fooheads.hide-nvim.connection
  "Namespace for handling connections to nvim on socket level.

  There are two types of connections:

  1) request/response: a connection intended to be used from the
     client to query and control nvim.

  2) event stream: a connection intended to only capture commands
     and events from nvim."
  (:require
    [clojure.spec.alpha :as s]
    [fooheads.hide-nvim.msg :as msg]
    [fooheads.hide-nvim.rpc :as rpc]))


(defn- get-channel
  "Calls nvim to get the channel. This call also returns
  the api-info, but we ignore it here"
  [istream ostream]
  (rpc/write-data ostream [0 1 "nvim_get_api_info" []])
  (let [response-msg (rpc/read-data istream)
        [_msgtype _msg-id _ msg] response-msg
        [channel _api-info] msg]
    channel))


(defn log-msg
  [connection msg]
  (let [ts (System/currentTimeMillis)
        make-log-entry (fn [log log-entry] (cons log-entry (take 999 log)))]
    (swap! connection update :log make-log-entry {:ts ts :msg msg})))


(defn create-connection
  "Creates a socket connection to nvim and figures out the
  nvim assigned channel for this socket. Returns an atom
  containing a map with keys :socket, :input-stream, :output-stream
  and :channel"
  [host port]
  (let [socket (java.net.Socket. host port)
        istream (.getInputStream socket)
        ostream (.getOutputStream socket)]
    (.setTcpNoDelay socket true)

    (atom
      {:socket socket
       :input-stream istream
       :output-stream ostream
       :msgid 300
       :channel (get-channel istream ostream)
       :log (list)})))


(defn data-available?
  "Returns true if there is data available on the :input-stream"
  [connection]
  (> (.available (:input-stream @connection)) 0))


(defn- send-request
  "Sends a request message over the connection."
  [connection method params]

  (let [ostream (:output-stream @connection)
        msgid (:msgid @connection)
        msg (s/unform ::msg/request-msg
                      {:type msg/request :msgid msgid :method method :params params})]
    (assert (s/valid? ::msg/request-msg msg))
    (swap! connection update :msgid inc)
    (log-msg connection msg)
    (rpc/write-data ostream msg)))


(defn receive-message-blocking
  "Receives a message over the connection. Will block until there is
  a message. Returns a conformed message or throws an exception if the
  message is non conformant."
  [connection]
  (let [msg (rpc/read-data (:input-stream @connection))]
        ;conformed-msg (s/conform ::msg/msg msg)]
    (log-msg connection msg)
    (if (s/valid? ::msg/msg msg)
      (let [[msg-type msg] (s/conform ::msg/msg msg)]
        (assoc msg :type msg-type))
      (throw (ex-info "Received an unknown message!" {:msg msg})))))


(defn receive-response-blocking
  "Receives a response message over the connection. Will block until there is
  a message. Returns a conformed response message or throws an exception if the
  message is non conformant."
  [connection]
  (let [msg (rpc/read-data (:input-stream @connection))
        conformed-msg (s/conform ::msg/response-msg msg)]
    (log-msg connection msg)
    (when-not (s/valid? ::msg/response-msg msg)
      (throw (ex-info "Received an message that was not a response message!" {:msg msg})))

    ;; Error types: 0 - Exception, 1 - Validation (see: nvim_get_api_info)
    (let [error (:error conformed-msg)
          result (:result conformed-msg)]
      (if error
        (let [error-msg (second error)]
          (throw (ex-info error-msg {:msg msg :conformed-msg conformed-msg})))
        result))))   ;; TODO: assoc type?


(defn receive-with-timeout
  "Receives a response message over the connection. Will timeout
  if there is no message present in (default) 3000 ms. :timeout-ms
  can be set in options."
  [receive-func connection & options]
;; TODO: bug?
  (let [options (merge options {:timeout-ms 3000})
        max-time (:timeout-ms options)]
    (loop [time-elapsed 0]
      (if (data-available? connection)
        (receive-func connection)
        (if (< time-elapsed max-time)
          (let [sleep-time 100]
            (Thread/sleep sleep-time)
            (recur (+ time-elapsed sleep-time)))
          (throw (ex-info "Timeout receiving response"
                          {:connection (dissoc @connection :log)
                           :options options})))))))


; (def receive-message (partial receive-with-timeout receive-message-blocking))
; (def receive-response (partial receive-with-timeout receive-response-blocking))
(def receive-response receive-response-blocking)


(defn call
  "Sends a request and waits for the response. Returns the response"
  [connection method params]
  (send-request connection method params)
  (receive-response connection))


(comment

  (def cc (create-connection "localhost" 7777))
  (prn (deref cc))

  (do
    (send-request cc "nvim_buf_line_count" [0])
    (receive-message-blocking cc))

  (do
    (send-request cc "nvim_buf_line_count" [0])
    (receive-response cc))

  (do
    (send-request cc "nvim_buf_get_lines" [0 0 10 false])
    (receive-response cc))

  (do
    (send-request cc "hihi_nvim_buf_line_count_hihi" [0])
    (receive-response cc))

  (do
    (send-request cc 19 [0])
    (receive-response cc))

  (call cc "nvim_buf_line_count" [0]))


