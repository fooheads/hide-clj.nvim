(ns fooheads.hide-nvim.connection-test
  (:require
    [clojure.spec.alpha :as s]
    [clojure.test :refer :all]
    [fooheads.hide-nvim.connection :refer :all]
    [fooheads.hide-nvim.rpc :as rpc]))

(def msg-data ["function-name" ["arg1" "arg2"]])

(deftest specs-tests
  (let [msg-data ["function-name" ["arg1" "arg2"]]]
    (is (true? (s/valid? :nvim/request-msg [0 msg-data])))
    (is (false? (s/valid? :nvim/request-msg [1 msg-data])))

    (is (false? (s/valid? :nvim/response-msg
                          [0 ["function-name" ["arg1" "arg2"]]])))
    (is (true? (s/valid? :nvim/response-msg
                         [1 ["function-name" ["arg1" "arg2"]]])))

    (is (false? (s/valid? :nvim/notification-msg
                          [0 ["function-name" ["arg1" "arg2"]]])))
    (is (true? (s/valid? :nvim/notification-msg
                         [2 ["function-name" ["arg1" "arg2"]]])))

    (is (true? (s/valid? :nvim/msg
                         [0 ["function-name" ["arg1" "arg2"]]])))
    (is (true? (s/valid? :nvim/msg
                         [1 ["function-name" ["arg1" "arg2"]]])))
    (is (true? (s/valid? :nvim/msg
                         [2 ["function-name" ["arg1" "arg2"]]])))
    (is (false? (s/valid? :nvim/msg
                          [3 ["function-name" ["arg1" "arg2"]]])))

    (is (true? (s/valid? :nvim/request-msg [0 msg-data])))))
