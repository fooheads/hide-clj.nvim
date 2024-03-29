;;(comment
;;  (ns fooheads.hide-nvim.refactor
;;    (:require [fooheads.hide.refactor :as r :reload true]
;;              [neovim.core :as n]))
;;
;;  (defn get-lines 
;;    "Returns the current buffer as a list of lines." 
;;    [client]
;;    (n/exec client (n/buf-get-lines (n/get-current-buf) 0 -1 false)))
;;
;;  (defn set-lines! 
;;    "Replace the content of the current buffer with `lines`." 
;;    [lines client]
;;    (n/exec client (n/buf-set-lines (n/get-current-buf) 0 -1 false lines)))
;;
;;  (defn get-cursor 
;;    "Returns the cursor (row,col), with the same (row,col) as
;;    is visual insode nvim."
;;    [client]
;;    (let [[row col] (n/exec client (n/win-get-cursor (n/get-current-win)))]
;;      [row (+ col 1)]))
;;
;;  (defn get-buffer 
;;    "Returns the full current buffer as a single string."
;;    [client]
;;    (->> client
;;        get-lines
;;        (clojure.string/join "\n")))
;;
;;  (defn move-to-let [client]
;;    (let [binding-name "*change-me*"
;;          code (get-buffer client)
;;          [row col] (get-cursor client)]
;;
;;      (let [refactored-code (r/move-to-let code row col binding-name)]
;;        (prn refactored-code)
;;        (-> refactored-code
;;            (clojure.string/split #"\n") 
;;            (set-lines! client)))))
;;
;;  (defn introduce-let [client]
;;    (let [binding-name "*change-me*"
;;          code (get-buffer client)
;;          [row col] (get-cursor client)]
;;
;;      (let [refactored-code (r/introduce-let code row col binding-name)]
;;        (-> refactored-code
;;            (clojure.string/split #"\n") 
;;            (set-lines! client)))))
;;
;;
