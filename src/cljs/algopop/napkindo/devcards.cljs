(ns algopop.napkindo.devcards
  (:require
    [devcards.core]
    [algopop.napkindo.main]
    [algopop.napkindo.firebase :as firebase])
  (:require-macros
    [devcards.core :refer [start-devcard-ui! defcard-rg]]))

(enable-console-print!)

(defonce firebase (firebase/init))

(defn init []
  (start-devcard-ui!))
