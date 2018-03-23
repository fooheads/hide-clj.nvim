(ns fooheads.hide-clj.nvim.core
  (:require [neovim-client.1.api :as api] 
            [neovim-client.nvim :as nvim]))

;; 
;; hide-clj.nvim 
;; 


(defn -main [& args]
  (println "Starting Hide for Neovim.")

  ; Start Neovim with: 
  ;   NVIM_LISTEN_ADDRESS=127.0.0.1:7777 nvim
  (let [conn (neovim-client.nvim/new 1 "localhost" 7777)
        x (atom 0)]

    ; This can be read from Neovim
    (api/command conn ":let g:is_running=1")

    ; TODO: Shouldn't we be able to call these functions from 
    ;       Neovim?
    (nvim/register-method!
      conn
      "foo"
      (fn [msg]
        (println "Clojure received '" msg "'")))

    (dotimes [n 60]
      (if (= 0 (mod n 10))
        (api/command conn (str ":echo 'plugin alive for " n " seconds.'")))
      (Thread/sleep 1000))

    ;; Let nvim know we're shutting down.
    (api/command conn ":let g:is_running=0")
    (api/command conn ":echo 'plugin stopping.'"))) 
