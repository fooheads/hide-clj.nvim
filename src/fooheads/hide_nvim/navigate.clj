(ns fooheads.hide-nvim.navigate
  (:require
    [fooheads.hide.navigate :as hn]
    [fooheads.hide-nvim.connection :as connection]
    [fooheads.hide-nvim.rpc :as rpc]))

(defn echo [connection s]
  (let [escaped (clojure.string/replace s "\"" "\\\"")
        echo-command (str ":echo \"" escaped "\"")]
    (connection/call connection "nvim_command" [echo-command])))

(defn get-cursor
  "Returns the cursor (row,col), with the same (row,col) as
  is visual inside nvim."
  [connection]
  (let [current-win (rpc/->Window 0)
        [row col] (connection/call connection "nvim_win_get_cursor" [current-win])]
    [row (+  col 1)]))

(defn set-cursor
  "Sets the cursor at (row,col), with the same (row,col) as
  is visual inside nvim."
  [connection row col]
  (let [current-win (rpc/->Window 0)]
    (connection/call connection "nvim_win_set_cursor" [current-win [row (- col 1)]])))

(defn get-lines
  "Returns the current buffer as a list of lines."
  [connection]
  (let [current-buffer (rpc/->Buffer 0)
        from 0
        to -1]
    (connection/call connection "nvim_buf_get_lines" [current-buffer from to false])))

(defn get-buffer
  "Returns the full current buffer as a single string."
  [connection]
  (->> connection
      get-lines
      (clojure.string/join "\n")))

(defn edit [connection full-path]
  (connection/call connection "nvim_command" [(str ":edit! " full-path)]))

(defn doc
  ([connection]
   (let [code (get-buffer connection)
         [row col] (get-cursor connection)]
     (doc connection code row col)))

  ([connection code row col]
   (let [doc-text (hn/doc code row col)]
     (if doc-text
       (echo connection doc-text)))))

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
  (let [code (get-buffer connection)

        [row col] (get-cursor connection)]
    (hn/get-namespace code row col)))





