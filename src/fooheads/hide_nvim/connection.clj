(ns fooheads.hide-nvim.connection
  "Namespace for handling connections to nvim on socket level.

  There are two types of connections:

  1) request/response: a connection intended to be used from the
     client to query and control nvim.

  2) event stream: a connection intended to only capture commands
     and events from nvim."

  (:require
    [clojure.spec.alpha :as s]
    [fooheads.hide-nvim.rpc :as rpc]))

(s/def :nvim/msgtype-request rpc/msgtype-request?)
(s/def :nvim/msgtype-response rpc/msgtype-response?)
(s/def :nvim/msgtype-notification rpc/msgtype-notification?)
(s/def :nvim/msgtype rpc/msgtype?)

(s/def :nvim/fn-args vector?)
(s/def :nvim/fn-name string?)
(s/def :nvim/fn-call (s/tuple :nvim/fn-name :nvim/fn-args))

(s/def :nvim/request-msg-data :nvim/fn-call)
(s/def :nvim/response-msg-data vector?)
(s/def :nvim/notification-msg-data :nvim/fn-call)

(s/def :nvim/request-msg
  (s/cat :msgtype :nvim/msgtype-request
         :msg-data :nvim/request-msg-data))

(s/def :nvim/response-msg
  (s/cat :msgtype :nvim/msgtype-response
         :msg-data :nvim/response-msg-data))

(s/def :nvim/notification-msg
  (s/cat :msgtype :nvim/msgtype-notification
         :msg-data :nvim/notification-msg-data))

(s/def :nvim/msg
  (s/or :msg :nvim/request-msg
        :msg :nvim/response-msg
        :msg :nvim/notification-msg))


(defn- get-channel
  "Calls nvim to get the channel. This call also returns
  the api-info, but we ignore it here"
  [istream ostream]
  (rpc/write-data ostream [0 1 "nvim_get_api_info" []])
  (let [response-msg (rpc/read-data istream)
        [msgtype msg-id _ msg] response-msg
        [channel api-info] msg]
    channel))

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
       :channel (get-channel istream ostream)})))

(defn data-available? [connection]
  "Returns true if there is data available on the
  :input-stream"
  (> (.available (:input-stream @connection)) 0))

(defn send-message
  "Sends a message over the connection. The msgtype must
  be a rpc/msgtype-request or a rpc/mes"
  [connection msg]
  {:pre [(s/valid? :nvim/msg msg)]}

  (let [channel (:channel @connection)
        ostream (:output-stream @connection)
        [msgtype fn-name args] msg
        msg [msgtype channel fn-name args]]
    (rpc/write-data ostream msg)))

(defn send-request
  "Sends a request message over the connection."
  [connection request-msg-data]
  {:pre [(s/valid? :nvim/request-msg-data request-msg-data)]}

  (let [channel (:channel @connection)
        ostream (:output-stream @connection)
        [fn-name args] request-msg-data
        msg [rpc/msgtype-request channel fn-name args]]
    (rpc/write-data ostream msg)))

(defn receive-response-blocking
  "Receives a response message over the connection. Will block
  until there is a message."
  [connection]
  {:post [(s/valid? :nvim/response-msg-data %)]}
  (let [response-msg (rpc/read-data (:input-stream @connection))]
    (let [[msgtype msg-id _ msg-data] response-msg]
      (if (= rpc/msgtype-response msgtype)
        msg-data
        (throw (ex-info "Received a message that was not a response message!"
                       {:response-message response-msg}))))))

(defn receive-response
  "Receives a response message over the connection. Will block
  until there is a message."
  [connection & options]
  {:post [(s/valid? :nvim/response-msg-data %)]}
  (let [options (merge options {:timeout-ms 3000})
        max-time (:timeout-ms options)]
    (loop [time-elapsed 0]
      (if (data-available? connection)
        (receive-response-blocking connection)
        (if (< time-elapsed max-time)
          (let [sleep-time 100]
            (Thread/sleep sleep-time)
            (recur (+ time-elapsed sleep-time)))
          (throw (ex-info "Timeout receiving response"
                          {:connection connection
                           :options options})))))))

(defn call
  "Sends a request and waits for the response. Returns the
  response"
  [connection request-msg-data]
  {:pre [(s/valid? :nvim/request-msg-data request-msg-data)]
   :post [(s/valid? :nvim/response-msg-data %)]}

  (send-request connection request-msg-data)
  (receive-response connection))

(comment

  (def cc (create-connection "localhost" 50433))

  (do
    (send-request cc ["nvim_buf_line_count" [0]])
    (receive-response cc))

  (call cc ["nvim_buf_line_count" [0]])

  (do
    (rpc/write-data (:output-stream @cc)
                    [0 (:channel @cc) "nvim_buf_line_count" [0]])
    (rpc/read-data (:input-stream @cc)))

  ;;
  ;; Specs
  ;;
  (s/conform :nvim/msg [0 "foo" ["bar"]])
  (s/conform :nvim/request-msg [0 "foo" ["bar"]])
  (s/conform :nvim/msg [3 "foo" ["bar"]])
  (s/explain :nvim/msg [3 "foo" ["bar"]])

  ;; not valid
  (s/conform :nvim/msg [0 "foo"])
  (s/conform :nvim/msg [3 "foo" ["bar"]]))





