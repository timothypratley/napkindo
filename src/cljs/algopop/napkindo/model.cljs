(ns algopop.napkindo.model
  (:require [reagent.core :as reagent]))

(defonce app-state
  (reagent/atom {}))

(defn set-route! [token]
  (swap! app-state assoc :route token))
