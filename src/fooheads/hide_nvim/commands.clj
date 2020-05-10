(ns fooheads.hide-nvim.commands
  "Command handling"
  (:require
    [fooheads.hide-nvim.navigate]))

(def command-map
  {:go-to-definition #'fooheads.hide-nvim.navigate/go-to-definition
   :doc #'fooheads.hide-nvim.navigate/doc})





