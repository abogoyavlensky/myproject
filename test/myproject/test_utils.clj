(ns myproject.test-utils
  (:require [integrant-extras.tests :as ig-extras]
            [myproject.db :as db]
            [reitit-extras.core :as reitit-extras]
            [ring.middleware.session.cookie :as ring-session-cookie]
            [ring.middleware.session.store :as ring-session-store]
            [ring.util.codec :as codec]))

(def ^:const CSRF-TOKEN-FORM-KEY :__anti-forgery-token)
(def ^:const CSRF-TOKEN-SESSION-KEY :ring.middleware.anti-forgery/anti-forgery-token)
; TODO: maybe remove
(def ^:const CSRF-TOKEN-HEADER "x-csrf-token")
(def ^:const TEST-CSRF-TOKEN "test-csrf-token")
(def ^:const TEST-SECRET-KEY "test-secret-key")

(defn- all-tables
  [db]
  (->> {:select [:name]
        :from [:sqlite_master]
        :where [:= :type "table"]}
       (db/exec! db)
       (map (comp keyword :name))))

(defn with-truncated-tables
  "Remove all data from all tables."
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

(defn add-csrf
  "Add CSRF token to request options for POST requests."
  ([request-opts]
   (add-csrf request-opts {}))
  ([request-opts session-data]
   (let [session-data (merge session-data
                             {:ring.middleware.anti-forgery/anti-forgery-token TEST-CSRF-TOKEN})]
     (-> request-opts
         (assoc :cookies (session-cookies session-data))
         (update :form-params assoc CSRF-TOKEN-FORM-KEY TEST-CSRF-TOKEN)))))
