(ns fooheads.hide-nvim.connection
  "Namespace for handling connections to nvim on socket level.

  There are two types of connections:

  1) request/response: a connection intended to be used from the
     client to query and control nvim.

  2) event stream: a connection intended to only capture commands
     and events from nvim."

  (:require
    [fooheads.hide-nvim.rpc :as rpc]))

(defn- get-channel
  "Calls nvim to get the channel. This call also returns
  the api-info, but we ignore it here"
  [istream ostream]
  (rpc/write-data ostream [0 1 "nvim_get_api_info" []])
  (let [response-msg (rpc/read-data istream)
        [msg-type msg-id _ msg] response-msg
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

(defn send-request
  "Sends a message over the connection."
  [connection msg]
  (let [channel (:channel @connection)
        ostream (:output-stream @connection)
        [fn-name args] msg
        msg [rpc/msgtype-request channel fn-name args]]
    (rpc/write-data ostream msg)))

(defn receive-response-blocking
  "Receives a response message over the connection. Will block
  until there is a message."
  [connection]
  (let [response-msg (rpc/read-data (:input-stream @connection))]
    (let [[msg-type msg-id _ msg] response-msg]
      (if (= rpc/msgtype-response msg-type)
        msg
        (throw (ex-info "Received a message that was not a response message!"
                       {:response-message response-msg}))))))

(defn receive-response
  "Receives a response message over the connection. Will block
  until there is a message."
  [connection & options]
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
  ([connection  msg]
   (send-request connection msg)
   (receive-response connection)))

(comment

  (def cc (create-connection "localhost" 50433))

  (do
    (send-request cc ["nvim_buf_line_count" [0]])
    (receive-response cc))

  (call cc ["nvim_buf_line_count" [0]])

  (do
    (rpc/write-data (:output-stream @cc)
                    [0 (:channel @cc) "nvim_buf_line_count" [0]])
    (rpc/read-data (:input-stream @cc))))




