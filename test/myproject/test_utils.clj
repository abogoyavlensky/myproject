(ns myproject.test-utils
  (:require [integrant-extras.tests :as ig-extras]
            [hickory.core :as hickory]
            [myproject.db :as db]
            [myproject.server :as server]
            [reitit-extras.core :as reitit-extras]
            [ring.middleware.session.cookie :as ring-session-cookie]
            [ring.middleware.session.store :as ring-session-store]
            [ring.util.codec :as codec]))

(def ^:const CSRF-TOKEN-FORM-KEY :__anti-forgery-token)
(def ^:const CSRF-TOKEN-SESSION-KEY :ring.middleware.anti-forgery/anti-forgery-token)
(def ^:const TEST-CSRF-TOKEN "test-csrf-token")
(def ^:const TEST-SECRET-KEY "test-secret-key")

(defn- all-tables
  "Get a list of all tables in the database, excluding the migrations table."
  [db]
  (->> {:select [:name]
        :from [:sqlite_master]
        :where [:= :type "table"]}
       (db/exec! db)
       (map (comp keyword :name))))

(defn with-truncated-tables
  "Remove all data from all tables except migrations."
  [f]
  (let [db (::db/db ig-extras/*test-system*)]
    (doseq [table (all-tables db)
            :when (not= :schema_version table)]
      (db/exec! db {:delete-from table}))
    (f)))

(defn encrypt-session-to-cookie
  "Encrypt session data to a cookie value using the server's session store."
  [session-data]
  (-> (ring-session-cookie/cookie-store
        {:key (reitit-extras/string->16-byte-array TEST-SECRET-KEY)})
      (ring-session-store/write-session nil session-data)
      (codec/form-encode)))

(defn session-cookies
  "Convert session data to cookies for a request."
  [session-data]
  {"ring-session" {:value (encrypt-session-to-cookie session-data)
                   :path "/"
                   :http-only true
                   :secure true}})

(defn response->hickory
  "Convert a Ring response body to a Hickory document."
  [response]
  (-> response
      :body
      (hickory/parse)
      (hickory/as-hickory)))

(defn db
  "Get the database connection from the test system."
  []
  (::db/db ig-extras/*test-system*))

(defn server
  "Get the server instance from the test system."
  []
  (::server/server ig-extras/*test-system*))
