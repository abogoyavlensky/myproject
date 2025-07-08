(ns myproject.test-utils
  (:require [integrant-extras.tests :as ig-extras]
            [myproject.db :as db]
            [buddy.sign.jwt :as jwt]
            [reitit-extras.core :as reitit-extras]
            [ring.middleware.session.cookie :as ring-session-cookie]
            [ring.middleware.session.store :as ring-session-store]
            [ring.util.codec :as codec])
  (:import [java.time Instant Duration]))

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

; Helper function to create valid JWT tokens for testing
(defn create-test-token
  ([email user-id] (create-test-token email user-id 24))
  ([email user-id hours-valid]
   (let [now (Instant/now)
         claims {:sub user-id
                 :email email
                 :exp (.getEpochSecond (.plus now (Duration/ofHours hours-valid)))
                 :iat (.getEpochSecond now)}]
     (jwt/sign claims TEST-SECRET-KEY {:alg :hs256}))))

(defn create-expired-token [email user-id]
  (let [past-time (Instant/parse "2020-01-01T00:00:00Z")
        claims {:sub user-id
                :email email
                :exp (.getEpochSecond past-time)
                :iat (.getEpochSecond past-time)}]
    (jwt/sign claims TEST-SECRET-KEY {:alg :hs256})))
