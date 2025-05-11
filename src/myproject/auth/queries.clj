(ns myproject.auth.queries
  (:require [myproject.db :as db]))

(defn create-user!
  [db {:keys [email password-hash]}]
  (db/exec-one! db {:insert-into :user
                    :values [{:email email
                              :password password-hash}]
                    :returning [:*]}))
