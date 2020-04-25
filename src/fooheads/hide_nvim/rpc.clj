(ns fooheads.hide-nvim.rpc
  (:require [clojure.tools.logging :as log]
            [msgpack.clojure-extensions]
            [msgpack.core :as msgpack]))

;;
;; The messagepack rpc message types
;;

(def msgtype-request 0)
(def msgtype-response 1)
(def msgtype-notify 2)

;;
;; Records for nvim specific types. They can all be treated
;; as integers according to https://neovim.io/doc/user/api.html#API,
;; but they're not interchangable. For serialization and msgpack
;; extention reasons, these are records here.

(defrecord Buffer [n])
(defrecord Window [id])
(defrecord Tabpage [handle])

;;
;; The msgpack extentions for Buffer, Window and Tabpage.
;;
(msgpack.macros/extend-msgpack
  Buffer
  0
  [buffer] (msgpack/pack (:n buffer))
  [bytes] (->Buffer (msgpack/unpack bytes)))

(msgpack.macros/extend-msgpack
  Window
  1
  [window] (msgpack/pack (:id window))
  [bytes] (->Window (msgpack/unpack bytes)))

(msgpack.macros/extend-msgpack
  Tabpage
  2
  [tabpage] (msgpack/pack (:handle tabpage))
  [bytes] (->Tabpage (msgpack/unpack bytes)))

;;
;; The lowest primitives when it comes to reading
;; and writing to nvim.
;;

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

;;
;; Sending requests and receiving responses.
;;

(defn send-request
  ([conn msg]
   (send-request conn (:channel @conn) msg))

  ([conn channel [fn-name args]]
   (let [seq-num (:seq-num @conn)
         msg [msgtype-request channel fn-name args]]
     (swap! conn
            #(-> %
                 (update :seq-num inc)
                 (update :messages conj msg)))
     (write-data (:output-stream @conn) msg))))

(defn recv-response [conn]
  (let [response-msg (read-data (:input-stream @conn))]
    (swap! conn update :messages conj response-msg)
    (let [[msg-type msg-id _ msg] response-msg]
      msg)))

(defn call
  ([conn msg]
   (call conn (:channel @conn) msg))
  ([conn channel msg]
   (send-request conn msg)
   (recv-response conn)))

