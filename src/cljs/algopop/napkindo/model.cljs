(ns algopop.napkindo.model
  (:require [reagent.core :as reagent]
            [cljs.tools.reader.edn :as edn]
            [clojure.walk :as walk]))

(defonce app-state
  (reagent/atom {}))

(defn set-route! [token]
  (swap! app-state assoc :route token))

(defn set-search! [s]
  (swap! app-state assoc :search s))

(defn maybe-update [m k f]
  (if (contains? m k)
    (update m k f)
    m))

(defn parse [drawing]
  (-> drawing
      (walk/keywordize-keys)
      (maybe-update :svg edn/read-string)
      (maybe-update :svg-attrs edn/read-string)
      (maybe-update :created #(js/Date. %))))

(def colors
  ["black"
   "#181818"
   "#282828"
   "#383838"
   "#585858"
   "#b8b8b8"
   "#d8d8d8"
   "#e8e8e8"
   "#f8f8f8"
   "#ab4642"
   "#dc9656"
   "#f7ca88"
   "#a1b56c"
   "#86c1b9"
   "#7cafc2"
   "#ba8baf"
   "#a16946"])
