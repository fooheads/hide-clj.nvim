(ns fooheads.hide-nvim.navigate
  (:require [fooheads.hide.navigate :as hn :reload true]
            [fooheads.hide-nvim.rpc :as rpc]
            [neovim.core :as n]))

;;; (defn get-lines
;;;   "Returns the current buffer as a list of lines."
;;;   [client]
;;;   (n/exec client (n/buf-get-lines (n/get-current-buf) 0 -1 false)))
;;;
;;; (defn get-cursor
;;;   "Returns the cursor (row,col), with the same (row,col) as
;;;   is visual inside nvim."
;;;   [client]
;;;   (let [[row col] (n/exec client (n/win-get-cursor (n/get-current-win)))]
;;;     [row (+ col 1)]))
;;;
;;; (defn set-cursor
;;;   "Sets the cursor at (row,col), with the same (row,col) as
;;;   is visual inside nvim."
;;;   [client row col]
;;;   (n/exec client (n/win-set-cursor (n/get-current-win) [row (- col 1)])))
;;;
;;; (defn get-buffer
;;;   "Returns the full current buffer as a single string."
;;;   [client]
;;;   (->> client
;;;       get-lines
;;;       (clojure.string/join "\n")))
;;;
;;; (defn escape [s]
;;;   (clojure.string/replace s "\"" "\\\""))
;;;
;;; (defn edit [client full-path]
;;;   (n/exec client (n/command (str ":edit " full-path))))
;;;
;;; (defn echo [client s]
;;;   (n/exec client (n/command (str ":echo \"" (escape s) "\""))))
;;;
;;; (defn echom [client s]
;;;   (n/exec client (n/command (str ":echom 'EKA!!! " s "'"))))
;;;
;;; (defn get-namespace [client]
;;;   (let [code (get-buffer client)
;;;         [row col] (get-cursor client)]
;;;     (hn/get-namespace code row col)))
;;;
;;; (defn go-to-definition
;;;   ([client]
;;;    (let [code (get-buffer client)
;;;          [row col] (get-cursor client)]
;;;      (go-to-definition client code row col)))
;;;
;;;   ([client code row col]
;;;    (let [[full-path new-row new-col] (hn/find-definition code row col)]
;;;      (edit client full-path)
;;;      (set-cursor client new-row new-col))))
;;;
;;; (defn doc
;;;   ([client]
;;;    (let [code (get-buffer client)
;;;          [row col] (get-cursor client)]
;;;      (doc client code row col)))
;;;
;;;   ([client code row col]
;;;    (let [doc-text (hn/doc code row col)]
;;;      (echo client doc-text))))

;;
;; New versions.
;;

(defn echo [connection s]
  (let [escaped (clojure.string/replace s "\"" "\\\"")
        echo-command (str ":echo \"" escaped "\"")
        command (n/command echo-command)]
    (rpc/call connection command)))

(defn get-cursor
  "Returns the cursor (row,col), with the same (row,col) as
  is visual inside nvim."
  [connection]
  (prn "get-cursor")
  (prn"connection" connection)
  (let [current-win 0
        [row col] (rpc/call connection (n/win-get-cursor current-win))]
    (prn "row" row "col" col)
    [row (+ col 1)]))

(defn set-cursor
  "Sets the cursor at (row,col), with the same (row,col) as
  is visual inside nvim."
  [connection row col]
  (let [current-win 0]
    (rpc/call connection (n/win-set-cursor current-win [row (- col 1)]))))

(defn get-lines
  "Returns the current buffer as a list of lines."
  [connection]
  (let [current-buffer 0
        from 0
        to -1]
    (rpc/call connection (n/buf-get-lines current-buffer from to false))))

(defn get-buffer
  "Returns the full current buffer as a single string."
  [connection]
  (prn "get-buffer")
  (->> connection
      get-lines
      (clojure.string/join "\n")))

(defn edit [connection full-path]
  (rpc/call connection (n/command (str ":edit " full-path))))

(defn doc
  ([connection]
   (let [code (get-buffer connection)
         [row col] (get-cursor connection)]
     (doc connection code row col)))

  ([connection code row col]
   (let [doc-text (hn/doc code row col)]
     (echo connection doc-text))))

(defn go-to-definition
  ([connection]
   (let [code (get-buffer connection)
         [row col] (get-cursor connection)]
     (go-to-definition connection code row col)))

  ([connection code row col]
   (let [[full-path new-row new-col] (hn/find-definition code row col)]
     (if full-path
       (do
         (edit connection full-path)
         (set-cursor connection new-row new-col))
       (echo connection "Can't find source file")))))

(defn get-namespace [connection]
  (prn "get-namespace")
  (let [code (get-buffer connection)

        [row col] (get-cursor connection)]
    (hn/get-namespace code row col)))




