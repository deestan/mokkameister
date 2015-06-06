(ns mokkameister.persistence
  (:require [mokkameister.system :refer [system]]
            [yesql.core :refer [defqueries]]))

(defqueries "sql/queries.psql")

(defn recreate-tables! [db]
  (try
    (drop-brewings-table! db)
    (catch Exception e)
    (finally (create-brewings-table! db))))

(defn persist-brew! [event]
  (let [db-conn (system :db)
        {:keys [slack-user brew-time]} event
        coffee-type (name (:coffee-type event))]
    (insert-brewing<! db-conn slack-user brew-time coffee-type)))

(defn latest-brews [db]
  (let [[last-regular] (find-last-regular-coffee db)
        [last-instant] (find-last-instant-coffee db)]
    {:regular last-regular
     :instant last-instant}))

(defn brew-stats [db]
  (letfn [(coffee-stats [type period]
            (-> (coffee-type-stats db type period)
                (first)
                :count))]
    (apply merge (for [[k v] [[:day "24 hours"]
                              [:week "1 week"]
                              [:month "1 month"]]]
                   {k {:regular (coffee-stats "regular" v)
                       :instant (coffee-stats "instant" v)}}))))
