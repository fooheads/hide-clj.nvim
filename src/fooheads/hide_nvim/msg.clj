(ns fooheads.hide-nvim.msg
  (:require
    [clojure.spec.alpha :as s]))


(def request 0)
(def response 1)
(def notification 2)

(s/def ::request #(= request %))
(s/def ::response #(= response %))
(s/def ::notification #(= notification %))

(s/def ::msgid integer?)
(s/def ::method string?)
(s/def ::params (s/coll-of any?))

(s/def ::error (s/or :no-error nil? :error-data any?))
(s/def ::result any?)


(s/def ::request-msg
  (s/cat :type ::request
         :msgid ::msgid
         :method ::method
         :params ::params))


(s/def ::response-msg
  (s/cat :type ::response
         :msgid ::msgid
         :error any?
         :result ::result))


(s/def ::notification-msg
  (s/cat :type ::notification
         :method ::method
         :params ::params))


(s/def ::msg
  (s/or :request-msg ::request-msg
        :response-msg ::response-msg
        :notification-msg ::notification-msg))


