(ns myproject.test-utils
  (:require [clj-http.client :as http]
            [clj-http.cookies :as cookies]
            [hickory.core :as hickory]
            [hickory.select :as select]
            [integrant-extras.tests :as ig-extras]
            [myproject.db :as db]))

(def ^:const CSRF-TOKEN-KEY :__anti-forgery-token)
(def ^:const CSRF-TOKEN-HEADER "x-csrf-token")

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

(defn get-csrf-token-and-cookies
  "Return CSRF token and cookies for given page to be used in POST request."
  [page-with-form-url]
  (let [cookie-store (cookies/cookie-store)
        response (http/get page-with-form-url {:cookie-store cookie-store})
        csrf-token (->> response
                        :body
                        (hickory/parse)
                        (hickory/as-hickory)
                        (select/select (select/id CSRF-TOKEN-KEY))
                        (first)
                        :attrs
                        :value)]
    {:csrf-token csrf-token
     :cookies (cookies/get-cookies cookie-store)}))

(defn get-logged-in-cookies
  "Get cookies for a logged-in user."
  [base-url {:keys [email password]}]
  (let [url (str base-url "/login")
        {:keys [cookies csrf-token]} (get-csrf-token-and-cookies url)
        cookie-store (cookies/cookie-store)
        _ (http/post url {:cookies cookies
                          :cookie-store cookie-store
                          :form-params {CSRF-TOKEN-KEY csrf-token
                                        :email email
                                        :password password}})]
    {:cookies (cookies/get-cookies cookie-store)}))
