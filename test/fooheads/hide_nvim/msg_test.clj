(ns fooheads.hide-nvim.msg-test
  (:require
    [clojure.spec.alpha :as s]
    [clojure.test :refer :all]
    [fooheads.hide-nvim.msg :refer :all]))

(def msg-data ["fname" ["arg1" "arg2"]])

(deftest specs-tests
  (let [msg-data ["fname" ["arg1" "arg2"]]]))
    ; (is (true? (s/valid? :nvim/request-msg [0 "fname" ["arg1" "arg2"]])))
    ; (is (false? (s/valid? :nvim/request-msg [1 "fname" ["arg1" "arg2"]])))

    ; (is (false? (s/valid? :nvim/response-msg [0 "fname" ["arg1" "arg2"]])))
    ; ;(is (true? (s/valid? :nvim/response-msg [1 "fname" ["arg1" "arg2"]])))

    ; (is (false? (s/valid? :nvim/notification-msg [0 "fname" ["arg1" "arg2"]])))
    ; (is (true? (s/valid? :nvim/notification-msg [2 "fname" ["arg1" "arg2"]])))

    ; (is (true? (s/valid? :nvim/msg [0 "fname" ["arg1" "arg2"]])))
    ; (is (true? (s/valid? :nvim/msg [1 "fname" ["arg1" "arg2"]])))
    ; (is (true? (s/valid? :nvim/msg [2 "fname" ["arg1" "arg2"]])))
    ; (is (false? (s/valid? :nvim/msg [3 "fname" ["arg1" "arg2"]])))))

    ; ;(is (true? (s/valid? :nvim/request-msg [0 msg-data])))))

(comment
  (s/valid? :nvim/msg [1 "fname" ["arg1" "arg2"]])
  (s/conform :nvim/msg [1 "fname" ["arg1" "arg2"]])
  (s/explain :nvim/msg [1 "fname" ["arg1" "arg2"]])
  (s/explain :nvim/response-msg [1 "fname" ["arg1" "arg2"]])

  (s/def :nvim/response-error-msg2 (s/spec (s/cat :code int? :message string?)))
  (s/def :nvim/response-error-msg-data (s/cat :error :nvim/response-error-msg2 :response-data nil?))
  (s/def :nvim/response-error-msg (s/cat :msgtype :nvim/msgtype-response :msg-data :nvim/response-error-msg-data))

  (s/conform :nvim/response-error-msg [1 [0 "Error msg"] nil])
  (s/explain :nvim/response-error-msg [1 [0 "Error msg"] nil])
  (s/explain :nvim/response-error-msg [1 [0 "Error msg"] nil])
  (s/explain :nvim/response-error-msg2 [0 "Error msg"]))


;; TODO: 
;; - Lowest level, name each argument and document it with examples.
;; - Split message interpretation into separate file?
;; - Send notification.



(deftest specs-conform-tests
  (testing "request message"
    (is (= {:msgtype 0, :msg-data {:fn-name "fname", :fn-args ["arg1" "arg2"]}} 
           (s/conform :nvim/request-msg [0 "fname" ["arg1" "arg2"]])))

    (is (= :clojure.spec.alpha/invalid
           (s/conform :nvim/request-msg [1 "fname" ["arg1" "arg2"]])))

    (is (= [:request {:msgtype 0, :msg-data {:fn-name "fname", :fn-args ["arg1" "arg2"]}}] 
           (s/conform :nvim/msg [0 "fname" ["arg1" "arg2"]]))))

  (testing "response message"
    (is (= {:msgtype 1, :msg-data {:error nil, :response-data ["arg1"]}} 
           (s/conform :nvim/response-success-msg [1 nil "arg1"])))

    (is (= {:msgtype 1, :msg-data {:error nil, :response-data [["arg1" "arg2"]]}} 
           (s/conform :nvim/response-success-msg [1 nil ["arg1" "arg2"]])))

    (is (= :clojure.spec.alpha/invalid
           (s/conform :nvim/response-success-msg [1 [0 "Invalid method: nvim_current_line"] nil])))

    ;; TODO: How to represent and destructure reponse errors?
    (is (= {:msgtype 1, :msg-data {:error nil, :response-data [["arg1" "arg2"]]}} 
           (s/conform :nvim/response-error-msg [1 [0 "Invalid method: nvim_current_line"] nil])
           (s/explain :nvim/response-error-msg [1 [0 "Invalid method: nvim_current_line"] nil])))
    

    (is (= :clojure.spec.alpha/invalid
           (s/conform :nvim/notification-msg [1 "fname" ["arg1" "arg2"]])))

    (is (= [:msg {:msgtype 2, :msg-data {:fn-name "fname", :fn-args ["arg1" "arg2"]}}] 
           (s/conform :nvim/msg [2 "fname" ["arg1" "arg2"]]))))

  (testing "notification message"
    (is (= {:msgtype 2, :msg-data {:fn-name "fname", :fn-args ["arg1" "arg2"]}} 
           (s/conform :nvim/notification-msg [2 "fname" ["arg1" "arg2"]])))

    (is (= :clojure.spec.alpha/invalid
           (s/conform :nvim/notification-msg [1 "fname" ["arg1" "arg2"]])))

    (is (= [:msg {:msgtype 2, :msg-data {:fn-name "fname", :fn-args ["arg1" "arg2"]}}] 
           (s/conform :nvim/msg [2 "fname" ["arg1" "arg2"]])))))

  ; (is (false? (s/valid? :nvim/request-msg [1 "fname" ["arg1" "arg2"]])))

  ; (is (false? (s/valid? :nvim/response-msg [0 "fname" ["arg1" "arg2"]])))
  ; ;(is (true? (s/valid? :nvim/response-msg [1 "fname" ["arg1" "arg2"]])))

  ; (is (false? (s/valid? :nvim/notification-msg [0 "fname" ["arg1" "arg2"]])))
  ; (is (true? (s/valid? :nvim/notification-msg [2 "fname" ["arg1" "arg2"]])))

  ; (is (true? (s/valid? :nvim/msg [0 "fname" ["arg1" "arg2"]])))
  ; (is (true? (s/valid? :nvim/msg [1 "fname" ["arg1" "arg2"]])))
  ; (is (true? (s/valid? :nvim/msg [2 "fname" ["arg1" "arg2"]])))
  ; (is (false? (s/valid? :nvim/msg [3 "fname" ["arg1" "arg2"]]))))

  ; ;(is (true? (s/valid? :nvim/request-msg [0 msg-data])))))


