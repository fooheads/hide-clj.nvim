(ns fooheads.hide-nvim.msg-test
  (:require
    [clojure.spec.alpha :as s]
    [clojure.test :refer :all]
    [fooheads.hide-nvim.msg :as msg]))


(deftest specs-conform-tests
  (testing "request message"
    (is (= {:type 0, :msgid 203 :method "fname" :params ["arg1" "arg2"]}
           (s/conform ::msg/request-msg [0 203 "fname" ["arg1" "arg2"]])))

    (is (= :clojure.spec.alpha/invalid
           (s/conform ::msg/request-msg [1 203 "fname" ["arg1" "arg2"]])))

    (is (= [:request-msg {:type 0 :msgid 203 :method "fname" :params ["arg1" "arg2"]}]
           (s/conform ::msg/msg [0 203 "fname" ["arg1" "arg2"]]))))

  (testing "response message"
    (is (= {:type 1 :msgid 203 :error nil :result ["arg1" "arg2"]}
           (s/conform ::msg/response-msg [1 203 nil ["arg1" "arg2"]])))

    (is (= {:type 1 :msgid 203 :error [0 "Invalid method: nvim_current_line"] :result nil}
           (s/conform ::msg/response-msg [1 203 [0 "Invalid method: nvim_current_line"] nil]))))

  (testing "notification message"
    (is (= {:type 2 :method "fname" :params ["arg1" "arg2"]}
           (s/conform ::msg/notification-msg [2 "fname" ["arg1" "arg2"]])))

    (is (= :clojure.spec.alpha/invalid
           (s/conform ::msg/notification-msg [1 "fname" ["arg1" "arg2"]])))))

