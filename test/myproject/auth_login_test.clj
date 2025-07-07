(ns myproject.auth-login-test
  (:require [clj-http.client :as http]
            [clojure.test :refer :all]
            [hickory.core :as hickory]
            [hickory.select :as select]
            [integrant-extras.tests :as ig-extras]
            [myproject.auth.queries :as queries]
            [myproject.db :as db]
            [myproject.test-utils :as utils]
            [reitit-extras.tests :as reitit-extras]))

(use-fixtures :once
  (ig-extras/with-system))

(use-fixtures :each
  utils/with-truncated-tables)

(deftest test-get-login-ok
  (let [server (:myproject.server/server ig-extras/*test-system*)
        base-url (reitit-extras/get-server-url server :host)
        url (str base-url "/login")
        body (-> (http/get url)
                 :body
                 (hickory/parse)
                 (hickory/as-hickory))]
    (is (= "Login"
           (->> body
                (select/select (select/tag :h2))
                (first)
                :content
                (first))))
    (is (= #{(name utils/CSRF-TOKEN-FORM-KEY) "email" "password"}
           (->> body
                (select/select (select/tag :input))
                (map (comp :name :attrs))
                (set))))
    (is (= {:hx-post "/login"
            :hx-target "#form-login"
            :id "form-login"}
           (dissoc (->> body (select/select (select/tag :form)) first :attrs)
                   :class :hx-swap)))))

(deftest test-post-login-success
  (let [server (:myproject.server/server ig-extras/*test-system*)
        db (::db/db ig-extras/*test-system*)
        base-url (reitit-extras/get-server-url server :host)
        login-url (str base-url "/login")
        test-email "user@example.com"
        test-password "password123"

        ;; First register a user
        _ (queries/create-user! db {:email test-email
                                    :password test-password})

        ; Now attempt to login using
        response (http/post login-url
                            {:cookies (utils/session-cookies
                                        {utils/CSRF-TOKEN-SESSION-KEY utils/TEST-CSRF-TOKEN})
                             :form-params {utils/CSRF-TOKEN-FORM-KEY utils/TEST-CSRF-TOKEN
                                           :email test-email
                                           :password test-password}})]

    (is (= 200 (:status response)))
    (is (= "/" (get (:headers response) "HX-Redirect")))))

(deftest test-post-login-invalid-email
  (let [server (:myproject.server/server ig-extras/*test-system*)
        base-url (reitit-extras/get-server-url server :host)
        url (str base-url "/login")
        invalid-email "not-an-email"

        ;; Try to login with an invalid email format
        response (http/post url {:cookies (utils/session-cookies
                                            {utils/CSRF-TOKEN-SESSION-KEY utils/TEST-CSRF-TOKEN})
                                 :form-params {utils/CSRF-TOKEN-FORM-KEY utils/TEST-CSRF-TOKEN
                                               :email invalid-email
                                               :password "some-password"}})

        ; Parse the response body to check for error message
        body (-> response
                 :body
                 (hickory/parse)
                 (hickory/as-hickory))
        error-messages (select/select (select/class :error-message) body)
        inputs (select/select (select/tag :input) body)]

    (is (= 1 (count error-messages)))
    (is (= 200 (:status response)))
    (is (= ["Invalid email format"] (-> error-messages first :content)))
    (is (= invalid-email (->> inputs
                              (filter #(= "email" (get-in % [:attrs :name])))
                              first
                              :attrs
                              :value)))))

(deftest test-post-login-incorrect-password
  (let [server (:myproject.server/server ig-extras/*test-system*)
        db (::db/db ig-extras/*test-system*)
        base-url (reitit-extras/get-server-url server :host)
        login-url (str base-url "/login")
        test-email "user2@example.com"
        correct-password "password123"
        incorrect-password "wrong-password"

        ; First register a user
        _ (queries/create-user! db {:email test-email
                                    :password correct-password})

        ; Now attempt to login with incorrect password
        response (http/post login-url {:cookies (utils/session-cookies
                                                  {utils/CSRF-TOKEN-SESSION-KEY utils/TEST-CSRF-TOKEN})
                                       :form-params {utils/CSRF-TOKEN-FORM-KEY utils/TEST-CSRF-TOKEN
                                                     :email test-email
                                                     :password incorrect-password}})

        ;; Parse the response body to check for error message
        body (-> response
                 :body
                 (hickory/parse)
                 (hickory/as-hickory))
        error-messages (select/select (select/class :error-message) body)
        inputs (select/select (select/tag :input) body)]

    (is (= 1 (count error-messages)))
    (is (= 200 (:status response)))
    (is (= ["Invalid email or password"] (-> error-messages first :content)))
    (is (= test-email (->> inputs
                           (filter #(= "email" (get-in % [:attrs :name])))
                           first
                           :attrs
                           :value)))))

(deftest test-post-login-nonexistent-user
  (let [server (:myproject.server/server ig-extras/*test-system*)
        base-url (reitit-extras/get-server-url server :host)
        login-url (str base-url "/login")
        nonexistent-email "nonexistent@example.com"

        ;; Attempt to login with a nonexistent user
        response (http/post login-url {:cookies (utils/session-cookies
                                                  {utils/CSRF-TOKEN-SESSION-KEY utils/TEST-CSRF-TOKEN})
                                       :form-params {utils/CSRF-TOKEN-FORM-KEY utils/TEST-CSRF-TOKEN
                                                     :email nonexistent-email
                                                     :password "some-password"}})

        ;; Parse the response body to check for error message
        body (-> response
                 :body
                 (hickory/parse)
                 (hickory/as-hickory))
        error-messages (select/select (select/class :error-message) body)
        inputs (select/select (select/tag :input) body)]

    (is (= 1 (count error-messages)))
    (is (= 200 (:status response)))
    (is (= ["Invalid email or password"] (-> error-messages first :content)))
    (is (= nonexistent-email (->> inputs
                                  (filter #(= "email" (get-in % [:attrs :name])))
                                  first
                                  :attrs
                                  :value)))))

(deftest test-get-login-already-logged-in
  (let [server (:myproject.server/server ig-extras/*test-system*)
        db (::db/db ig-extras/*test-system*)
        base-url (reitit-extras/get-server-url server :host)
        login-url (str base-url "/login")
        test-email "user@example.com"
        test-password "password123"
        
        ; Create a user for testing
        user (queries/create-user! db {:email test-email
                                       :password test-password})
        
        ; Try to access login page while already logged in
        response (http/get login-url {:redirect-strategy :none
                                      :cookies (utils/session-cookies {:identity user})})]

    ; Should get a redirect to home page
    (is (= 302 (:status response)))
    (is (= "/" (get-in response [:headers "Location"])))))

(deftest test-post-login-missing-email
  (let [server (:myproject.server/server ig-extras/*test-system*)
        base-url (reitit-extras/get-server-url server :host)
        login-url (str base-url "/login")

        ;; Try to login with missing email
        response (http/post login-url {:cookies (utils/session-cookies
                                                  {utils/CSRF-TOKEN-SESSION-KEY utils/TEST-CSRF-TOKEN})
                                       :form-params {utils/CSRF-TOKEN-FORM-KEY utils/TEST-CSRF-TOKEN
                                                     :password "some-password"}})

        ;; Parse the response body to check for error message
        body (-> response
                 :body
                 (hickory/parse)
                 (hickory/as-hickory))
        error-messages (select/select (select/class :error-message) body)]

    (is (= 200 (:status response)))
    (is (pos? (count error-messages)))))

(deftest test-post-login-missing-password
  (let [server (:myproject.server/server ig-extras/*test-system*)
        base-url (reitit-extras/get-server-url server :host)
        login-url (str base-url "/login")

        ;; Try to login with missing password
        response (http/post login-url {:cookies (utils/session-cookies
                                                  {utils/CSRF-TOKEN-SESSION-KEY utils/TEST-CSRF-TOKEN})
                                       :form-params {utils/CSRF-TOKEN-FORM-KEY utils/TEST-CSRF-TOKEN
                                                     :email "test@example.com"}})

        ;; Parse the response body to check for error message
        body (-> response
                 :body
                 (hickory/parse)
                 (hickory/as-hickory))
        error-messages (select/select (select/class :error-message) body)]

    (is (= 200 (:status response)))
    (is (pos? (count error-messages)))))

(deftest test-post-login-empty-fields
  (let [server (:myproject.server/server ig-extras/*test-system*)
        base-url (reitit-extras/get-server-url server :host)
        login-url (str base-url "/login")

        ;; Try to login with empty email and password
        response (http/post login-url {:cookies (utils/session-cookies
                                                  {utils/CSRF-TOKEN-SESSION-KEY utils/TEST-CSRF-TOKEN})
                                       :form-params {utils/CSRF-TOKEN-FORM-KEY utils/TEST-CSRF-TOKEN
                                                     :email ""
                                                     :password ""}})

        ;; Parse the response body to check for error message
        body (-> response
                 :body
                 (hickory/parse)
                 (hickory/as-hickory))
        error-messages (select/select (select/class :error-message) body)]

    (is (= 200 (:status response)))
    (is (pos? (count error-messages)))))

(deftest test-post-logout
  (let [server (:myproject.server/server ig-extras/*test-system*)
        db (::db/db ig-extras/*test-system*)
        base-url (reitit-extras/get-server-url server :host)
        logout-url (str base-url "/logout")
        test-email "user@example.com"
        test-password "password123"
        
        ;; Create a user for testing
        user (queries/create-user! db {:email test-email
                                       :password test-password})
        
        ;; Logout while logged in
        response (http/post logout-url {:cookies (utils/session-cookies
                                                   {utils/CSRF-TOKEN-SESSION-KEY utils/TEST-CSRF-TOKEN
                                                    :identity user})
                                        :form-params {utils/CSRF-TOKEN-FORM-KEY utils/TEST-CSRF-TOKEN}})]

    ;; Should redirect to home page after logout
    (is (= 200 (:status response)))
    (is (= "/" (get (:headers response) "HX-Redirect")))))

(deftest test-post-logout-unauthenticated
  (let [server (:myproject.server/server ig-extras/*test-system*)
        base-url (reitit-extras/get-server-url server :host)
        logout-url (str base-url "/logout")
        
        ;; Try to logout without being logged in
        response (http/post logout-url {:cookies (utils/session-cookies
                                                   {utils/CSRF-TOKEN-SESSION-KEY utils/TEST-CSRF-TOKEN})
                                        :form-params {utils/CSRF-TOKEN-FORM-KEY utils/TEST-CSRF-TOKEN}})]

    ;; Should still work (logout is idempotent)
    (is (= 200 (:status response)))
    (is (= "/" (get (:headers response) "HX-Redirect")))))
