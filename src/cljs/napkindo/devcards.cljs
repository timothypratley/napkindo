(ns napkindo.devcards
  (:require
    [devcards.core]
    [napkindo.main]
    [napkindo.firebase :as firebase])
  (:require-macros
    [devcards.core :refer [start-devcard-ui! defcard-rg]]))

(enable-console-print!)

(defonce firebase (firebase/init))

(defn init []
  (start-devcard-ui!))
