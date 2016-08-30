(ns algopop.napkindo.db
  (:require
    [algopop.napkindo.combinatorics :as combinatorics]
    [cljs.tools.reader.edn :as edn]
    [reagent.core :as reagent]
    [posh.reagent :refer [pull q posh! transact!]]
    [datascript.core :as d]
    [clojure.set :as set])
  (:require-macros
    [reagent.ratom :refer [reaction]]))

(def schema
  {:to {:db/type :db.type/ref}
   :from {:db/type :db.type/ref}})

(def the-real-schema
  {:node/name
   :node/body
   :anchor/from-node
   :anchor/from-subregion
   :anchor/to-node
   :anchor/to-subregion
   :set/anchors
   :set/query})

(defn init [conns uid]
  (let [conn (d/create-conn schema)]
    (posh! conn)
    (swap! conns assoc uid conn)
    conn))

(defn add-conn [conns uid db]
  (swap! conns assoc uid
         (let [conn (d/create-conn schema)]
           (when db
             (d/reset-conn! conn (edn/read-string {:readers d/data-readers} db)))
           (posh! conn)
           conn)))

(defn add-entity [conn e]
  (transact! conn [e]))

(defn add-entities [conn es]
  (transact! conn es))

(defn retract [conn id]
  (transact! conn [[:db.fn/retractEntity id]]))

(defn node-edges [conn id]
  ;; TODO: make more datalogy
  (map first (set/union
               @(q [:find '?e
                    :where
                    ['?e :from id]]
                   conn)
               @(q [:find '?e
                    :where
                    ['?e :to id]]
                   conn))))

(defn retract-node [conn id]
  (doseq [e (node-edges conn id)]
    (transact! conn [[:db.fn/retractEntity e]]))
  (transact! conn [[:db.fn/retractEntity id]]))

(defn nodes-q [conn]
  (q '[:find ?e ?name
       :where
       [?e :name ?name]]
     conn))

(defn nodes [conns]
  (reaction
    (doall
      (for [[uid conn] @conns
            [id name] @(nodes-q conn)]
        {:db/id (str uid "-" id)
         :uid uid
         :name name}))))

(defn edges-q [conn]
  (q '[:find ?e ?from ?to
       :where
       [?e :from ?from]
       [?e :to ?to]]
     conn))

(defn edges [conns]
  (reaction
    (doall
      (concat
        (for [[uid conn] @conns
              [id from to] @(edges-q conn)]
          {:db/id (str uid "-" id)
           :uid uid
           :from (str uid "-" from)
           :to (str uid "-" to)})
        (for [[name nodes] (group-by :name @(nodes conns))
              [a b] (combinatorics/combinations nodes 2)]
          {:db/id (str (:db/id a) "-" (:db/id b))
           :from (:db/id a)
           :to (:db/id b)})))))
