(ns myproject.queries
  (:require [myproject.db :as db]))

(defn get-movie-list
  [db]
  (db/exec! db {:select [:*]
                :from [:movie]
                :order-by [:id]}))

(defn create-movie
  [db {:keys [title year director]}]
  (db/exec-one! db {:insert-into :movie
                    :values [{:title title
                              :year year
                              :director director}]
                    :returning [:*]}))

(defn delete-movie
  [db {:keys [id]}]
  (db/exec-one! db {:delete-from :movie
                    :where [:= :id id]
                    :returning [:*]}))
