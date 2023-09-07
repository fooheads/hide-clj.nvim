(ns fooheads.hide-nvim.rpc-test
  (:require
    [clojure.spec.alpha :as s]
    [clojure.test :refer :all]
    [fooheads.hide-nvim.rpc :as rpc]
    [msgpack.core :as msgpack]))


(defn loopback-data
  "Writes data using rpc/write-data to a temporary output stream,
  and reads the data back rpc/read-data. Supports testing msgpack
  serialization and deserialization."
  [data]
  (let [ostream (java.io.ByteArrayOutputStream.)]
    (rpc/write-data ostream data)
    (let [istream (java.io.ByteArrayInputStream. (.toByteArray ostream))]
      (rpc/read-data istream))))


(deftest rpc-read-write-test
  (is (= [0 "func" ["arg1" "arg2"]]
         (loopback-data [0 "func" ["arg1" "arg2"]]))))


(deftest msgpack-test
  (is (= (rpc/->Buffer 17)
         (msgpack/unpack (msgpack/pack (rpc/->Buffer 17)))))

  (is (= (rpc/->Window 17)
         (msgpack/unpack (msgpack/pack (rpc/->Window 17)))))

  (is (= (rpc/->Tabpage 17)
         (msgpack/unpack (msgpack/pack (rpc/->Tabpage 17)))))

  (is (not= (rpc/->Buffer 17)
            (msgpack/unpack (msgpack/pack (rpc/->Window 17))))
      "Buffers and Windows are not the same, even though they
      have the same id"))


(deftest msgpack-details-test
  (testing "The actual serialization of the Buffer, Window
           and Tabpage types"

    (is (= [-44 0 17]
           (map int (msgpack/pack (rpc/->Buffer 17)))))

    (is (= [-44 1 17]
           (map int (msgpack/pack (rpc/->Window 17)))))

    (is (= [-44 2 17]
           (map int (msgpack/pack (rpc/->Tabpage 17)))))))

