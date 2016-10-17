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
