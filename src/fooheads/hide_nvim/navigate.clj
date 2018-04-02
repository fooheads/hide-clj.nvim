(ns fooheads.hide-nvim.navigate
  (:require [fooheads.hide.navigate :as hn :reload true]
            [neovim.core :as n]))

(defn get-lines 
  "Returns the current buffer as a list of lines." 
  [client]
  (n/exec client (n/buf-get-lines (n/get-current-buf) 0 -1 false)))

(defn get-cursor 
  "Returns the cursor (row,col), with the same (row,col) as
  is visual inside nvim."
  [client]
  (let [[row col] (n/exec client (n/win-get-cursor (n/get-current-win)))]
    [row (+ col 1)]))

(defn set-cursor 
  "Sets the cursor at (row,col), with the same (row,col) as
  is visual inside nvim."
  [client row col]
  (n/exec client (n/win-set-cursor (n/get-current-win) [row (- col 1)])))

(defn get-buffer 
  "Returns the full current buffer as a single string."
  [client]
  (->> client
      get-lines
      (clojure.string/join "\n")))

(defn edit [client full-path]
  (n/exec client (n/command (str ":edit " full-path))))

(defn get-namespace [client]
  (let [code (get-buffer client)
        [row col] (get-cursor client)]
    (hn/get-namespace code row col)))

(defn go-to-definition 
  ([client]
   (let [code (get-buffer client)
         [row col] (get-cursor client)]
     (go-to-definition client code row col)))

  ([client code row col]
   (let [[full-path new-row new-col] (hn/find-definition code row col)]
     (edit client full-path)
     (set-cursor client new-row new-col))))
