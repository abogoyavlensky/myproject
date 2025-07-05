(ns myproject.test-utils
  (:require [clj-http.client :as http]
            [clj-http.cookies :as cookies]
            [hickory.core :as hickory]
            [hickory.select :as select]
            [integrant-extras.tests :as ig-extras]
            [reitit-extras.core :as reitit-extras]
            [myproject.db :as db]
            [ring.middleware.session.cookie :as ring-session-cookie]
            [ring.middleware.session.store :as ring-session-store]
            [ring.middleware.anti-forgery :as anti-forgery]))

(def ^:const CSRF-TOKEN-KEY :__anti-forgery-token)
(def ^:const CSRF-TOKEN-HEADER "x-csrf-token")
(def ^:const TEST-CSRF-TOKEN "test-csrf-token-123")

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
  ([page-with-form-url]
   (get-csrf-token-and-cookies page-with-form-url nil))
  ([page-with-form-url cookies]
   (let [cookie-store (cookies/cookie-store)
         response (http/get page-with-form-url
                            (cond-> {:cookie-store cookie-store}
                              (some? cookies) (assoc :cookies cookies)))
         csrf-token (->> response
                         :body
                         (hickory/parse)
                         (hickory/as-hickory)
                         (select/select (select/id CSRF-TOKEN-KEY))
                         (first)
                         :attrs
                         :value)]
     {:csrf-token csrf-token
      :cookies (cookies/get-cookies cookie-store)})))

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

(defn post-with-csrf
  "Simplified POST request with CSRF token handling.
   Automatically gets CSRF token from the same URL before posting."
  ([url form-params]
   (post-with-csrf url form-params {}))
  ([url form-params opts]
   (let [{:keys [csrf-token cookies]} (get-csrf-token-and-cookies url (:cookies opts))]
     (http/post url
                (merge opts
                       {:cookies cookies
                        :form-params (assoc form-params CSRF-TOKEN-KEY csrf-token)})))))


(defn with-mock-csrf-token
  "Test fixture that mocks CSRF token generation for the entire test."
  [test-fn]
  (with-redefs [anti-forgery/*anti-forgery-token* TEST-CSRF-TOKEN]
    (test-fn)))


(defn post-with-mock-csrf
  "POST request with mocked CSRF token - no GET request needed."
  ([url form-params]
   (post-with-mock-csrf url form-params {}))
  ([url form-params opts]
   ;(with-redefs [anti-forgery/*anti-forgery-token* TEST-CSRF-TOKEN]
   (let [cookie-store (cookies/cookie-store)]
     (http/post url
                (merge opts
                       {:cookie-store cookie-store
                        :form-params (assoc form-params CSRF-TOKEN-KEY TEST-CSRF-TOKEN)})))))

(defn post-with-bypassed-csrf
  "POST request with bypassed CSRF validation."
  ([url form-params]
   (post-with-bypassed-csrf url form-params {}))

  ([url form-params opts]
   (with-redefs [anti-forgery/valid-request? (constantly true)]
     (http/post url (merge opts {:form-params form-params})))))


(defn post-with-session-csrf
  "POST request maintaining session for CSRF token."
  ([url form-params]
   (post-with-session-csrf url form-params {}))
  ([url form-params opts]
   (let [cookie-store (cookies/cookie-store)
         ;; First request to establish session
         _ (http/get url {:cookie-store cookie-store})
         ;; Use the established session for POST
         response (with-redefs [anti-forgery/*anti-forgery-token* TEST-CSRF-TOKEN]
                    (http/post url (merge opts
                                          {:cookie-store cookie-store
                                           :form-params (assoc form-params CSRF-TOKEN-KEY TEST-CSRF-TOKEN)})))]
     response)))


(defn post-with-mocked-get-token
  ([url form-params]
   (post-with-mocked-get-token url form-params {}))

  ([url form-params opts]
   ;(with-redefs [ring.middleware.anti-forgery.session/session-token (constantly TEST-CSRF-TOKEN)]
   (with-redefs [ring.middleware.anti-forgery.session/random-base64 (constantly TEST-CSRF-TOKEN)]
     (let [cookie-store (cookies/cookie-store)]
       (http/post url
                  (merge opts
                         {:cookie-store cookie-store
                          :form-params (assoc form-params CSRF-TOKEN-KEY TEST-CSRF-TOKEN)}))))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; Custom session

(defn create-session-store
  "Create a session store using the same configuration as the server."
  []
  (ring-session-cookie/cookie-store
    {:key (reitit-extras/string->16-byte-array "test-secret-key")}))

(defn encrypt-session-to-cookie
  "Encrypt session data to a cookie value using the server's session store."
  [session-data]
  (let [session-store (create-session-store)]
    (ring-session-store/write-session session-store nil session-data)))

(defn post-with-custom-session
  "POST request with custom session data."
  [url form-params]
  (let [session-data {:ring.middleware.anti-forgery/anti-forgery-token TEST-CSRF-TOKEN}
        encrypted-session-value (ring.util.codec/form-encode (encrypt-session-to-cookie session-data))
        session-cookies {"ring-session" {:value encrypted-session-value
                                         :path "/"
                                         :http-only true
                                         :secure true}}]
    (http/post url {:cookies session-cookies
                    :form-params (assoc form-params CSRF-TOKEN-KEY TEST-CSRF-TOKEN)})))


(comment
  ;(def STORE (atom (create-session-store)))
  (let [store (create-session-store)
        store2 (create-session-store)
        session-data {:ring.middleware.anti-forgery/anti-forgery-token TEST-CSRF-TOKEN}
        ;encrypted (ring-session-store/write-session @STORE nil session-data)
        encrypted "kvLfPNJ9SxPmW5+0EzIOySrBICuBV9ToADjCmkS8O8fs05W948roisgbq20fj0WYhZIS7tEYphymA3lf18x1CrxTRn87iBB/x77HeI6EPNCiUAzswyreds3aiwfXd2ZA--aGjMTT3EtTvxCCwPdGq9uYxKdwZlTEJjHEhvLYFC9Ps="]))
    ;(ring.util.codec/form-encode encrypted)))
    ;(ring-session-store/read-session store2 encrypted)))
