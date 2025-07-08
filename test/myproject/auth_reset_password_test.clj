(ns myproject.auth-reset-password-test
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

(deftest test-get-reset-password-valid-token
  (let [server (:myproject.server/server ig-extras/*test-system*)
        db (::db/db ig-extras/*test-system*)
        base-url (reitit-extras/get-server-url server :host)
        test-email "user@example.com"
        test-password "password123"
        
        ; Create a user for testing
        user (queries/create-user! db {:email test-email
                                       :password test-password})
        token (utils/create-test-token test-email (:id user))
        url (str base-url "/reset-password?token=" token)
        
        ; Request reset password page with valid token
        response (http/get url)
        body (-> response
                 :body
                 (hickory/parse)
                 (hickory/as-hickory))]

    ; Should show reset password form
    (is (= 200 (:status response)))
    
    ; Check page title
    (is (= "Reset Your Password"
           (->> body
                (select/select (select/tag :h2))
                (first)
                :content
                (first))))
    
    ; Check form has required fields
    (let [inputs (->> body
                     (select/select (select/tag :input))
                     (map (comp :name :attrs))
                     (set))]
      (is (contains? inputs "password"))
      (is (contains? inputs "confirm-password"))
      (is (contains? inputs "token"))
      (is (contains? inputs (name utils/CSRF-TOKEN-FORM-KEY))))
    
    ; Check email is displayed
    (is (some? (->> body
                    (select/select (select/find-in-text #".*user@example.com.*"))
                    (first))))))

(deftest test-get-reset-password-invalid-token
  (let [server (:myproject.server/server ig-extras/*test-system*)
        base-url (reitit-extras/get-server-url server :host)
        invalid-token "invalid.jwt.token"
        url (str base-url "/reset-password?token=" invalid-token)
        
        ; Request reset password page with invalid token
        response (http/get url {:throw-exceptions false})
        body (-> response
                 :body
                 (hickory/parse)
                 (hickory/as-hickory))]

    ; Should show error page with 400 status
    (is (= 400 (:status response)))
    (is (some? (->> body
                    (select/select (select/find-in-text #".*invalid.*"))
                    (first))))))

(deftest test-get-reset-password-expired-token
  (let [server (:myproject.server/server ig-extras/*test-system*)
        db (::db/db ig-extras/*test-system*)
        base-url (reitit-extras/get-server-url server :host)
        test-email "user@example.com"
        test-password "password123"
        
        ; Create a user for testing
        user (queries/create-user! db {:email test-email
                                       :password test-password})
        expired-token (utils/create-expired-token test-email (:id user))
        url (str base-url "/reset-password?token=" expired-token)
        
        ; Request reset password page with expired token
        response (http/get url {:throw-exceptions false})]

    ; Should show error page with 400 status
    (is (= 400 (:status response)))))

(deftest test-get-reset-password-missing-token
  (let [server (:myproject.server/server ig-extras/*test-system*)
        base-url (reitit-extras/get-server-url server :host)
        url (str base-url "/reset-password")
        
        ; Request reset password page without token parameter
        response (http/get url {:throw-exceptions false})]

    ; Should return error (parameter validation should fail)
    (is (= 400 (:status response)))))

(deftest test-get-reset-password-already-logged-in
  (let [server (:myproject.server/server ig-extras/*test-system*)
        db (::db/db ig-extras/*test-system*)
        base-url (reitit-extras/get-server-url server :host)
        test-email "user@example.com"
        test-password "password123"
        
        ; Create a user for testing
        user (queries/create-user! db {:email test-email
                                       :password test-password})
        token (utils/create-test-token test-email (:id user))
        url (str base-url "/reset-password?token=" token)
        
        ; Try to access reset password page while already logged in
        response (http/get url {:redirect-strategy :none
                                :cookies (utils/session-cookies {:identity user})})]

    ; Should get a redirect to home page (wrap-already-logged-in middleware)
    (is (= 302 (:status response)))
    (is (= "/" (get-in response [:headers "Location"])))))

; POST /reset-password tests
(deftest test-post-reset-password-valid
  (let [server (:myproject.server/server ig-extras/*test-system*)
        db (::db/db ig-extras/*test-system*)
        base-url (reitit-extras/get-server-url server :host)
        url (str base-url "/reset-password")
        test-email "user@example.com"
        original-password "original-password"
        new-password "new-secure-password"
        
        ; Create a user for testing
        user (queries/create-user! db {:email test-email
                                       :password original-password})
        token (utils/create-test-token test-email (:id user))
        
        ; Submit password reset
        response (http/post url {:cookies (utils/session-cookies
                                            {utils/CSRF-TOKEN-SESSION-KEY utils/TEST-CSRF-TOKEN})
                                 :form-params {utils/CSRF-TOKEN-FORM-KEY utils/TEST-CSRF-TOKEN
                                               :password new-password
                                               :confirm-password new-password
                                               :token token}})
        
        ; Parse response body
        body (-> response
                 :body
                 (hickory/parse)
                 (hickory/as-hickory))]

    ; Should show success message
    (is (= 200 (:status response)))
    (is (some? (->> body
                    (select/select (select/find-in-text #".*Password Reset Successful.*"))
                    (first))))
    
    ; Verify password was actually changed in database
    (let [updated-user (queries/get-user db test-email)]
      (is (not= (:password user) (:password updated-user))))))

(deftest test-post-reset-password-invalid-token
  (let [server (:myproject.server/server ig-extras/*test-system*)
        base-url (reitit-extras/get-server-url server :host)
        url (str base-url "/reset-password")
        invalid-token "invalid-jwt-token"
        new-password "new-secure-password"
        
        ; Submit password reset with invalid token
        response (http/post url {:throw-exceptions false
                                 :cookies (utils/session-cookies
                                            {utils/CSRF-TOKEN-SESSION-KEY utils/TEST-CSRF-TOKEN})
                                 :form-params {utils/CSRF-TOKEN-FORM-KEY utils/TEST-CSRF-TOKEN
                                               :password new-password
                                               :confirm-password new-password
                                               :token invalid-token}})
        
        ; Parse response body
        body (-> response
                 :body
                 (hickory/parse)
                 (hickory/as-hickory))]

    ; Should show error page
    (is (= 200 (:status response)))
    (is (some? (->> body
                    (select/select (select/find-in-text #".*Invalid or expired token.*"))
                    (first))))))

(deftest test-post-reset-password-expired-token
  (let [server (:myproject.server/server ig-extras/*test-system*)
        db (::db/db ig-extras/*test-system*)
        base-url (reitit-extras/get-server-url server :host)
        url (str base-url "/reset-password")
        test-email "user@example.com"
        original-password "original-password"
        new-password "new-secure-password"
        
        ; Create a user for testing
        user (queries/create-user! db {:email test-email
                                       :password original-password})
        expired-token (utils/create-expired-token test-email (:id user))
        
        ; Submit password reset with expired token
        response (http/post url {:cookies (utils/session-cookies
                                            {utils/CSRF-TOKEN-SESSION-KEY utils/TEST-CSRF-TOKEN})
                                 :form-params {utils/CSRF-TOKEN-FORM-KEY utils/TEST-CSRF-TOKEN
                                               :password new-password
                                               :confirm-password new-password
                                               :token expired-token}})
        body (-> response
                 :body
                 (hickory/parse)
                 (hickory/as-hickory))]

    ; Should show error page
    (is (= 200 (:status response)))
    (is (some? (->> body
                    (select/select (select/find-in-text #".*Invalid or expired token.*"))
                    (first))))))

(deftest test-post-reset-password-mismatch
  (let [server (:myproject.server/server ig-extras/*test-system*)
        db (::db/db ig-extras/*test-system*)
        base-url (reitit-extras/get-server-url server :host)
        url (str base-url "/reset-password")
        test-email "user@example.com"
        original-password "original-password"
        new-password "new-secure-password"
        different-password "different-password"
        
        ; Create a user for testing
        user (queries/create-user! db {:email test-email
                                       :password original-password})
        token (utils/create-test-token test-email (:id user))
        
        ; Submit password reset with mismatched passwords
        response (http/post url {:cookies (utils/session-cookies
                                            {utils/CSRF-TOKEN-SESSION-KEY utils/TEST-CSRF-TOKEN})
                                 :form-params {utils/CSRF-TOKEN-FORM-KEY utils/TEST-CSRF-TOKEN
                                               :password new-password
                                               :confirm-password different-password
                                               :token token}})
        
        ; Parse response body to check for error message
        body (-> response
                 :body
                 (hickory/parse)
                 (hickory/as-hickory))
        error-messages (select/select (select/class :error-message) body)]

    ; Should show validation error
    (is (= 200 (:status response)))
    (is (= 1 (count error-messages)))
    (is (= ["Passwords do not match"] (-> error-messages first :content)))))

(deftest test-post-reset-password-too-short
  (let [server (:myproject.server/server ig-extras/*test-system*)
        db (::db/db ig-extras/*test-system*)
        base-url (reitit-extras/get-server-url server :host)
        url (str base-url "/reset-password")
        test-email "user@example.com"
        original-password "original-password"
        short-password "123"
        
        ; Create a user for testing
        user (queries/create-user! db {:email test-email
                                       :password original-password})
        token (utils/create-test-token test-email (:id user))
        
        ; Submit password reset with too short password
        response (http/post url {:cookies (utils/session-cookies
                                            {utils/CSRF-TOKEN-SESSION-KEY utils/TEST-CSRF-TOKEN})
                                 :form-params {utils/CSRF-TOKEN-FORM-KEY utils/TEST-CSRF-TOKEN
                                               :password short-password
                                               :confirm-password short-password
                                               :token token}})
        
        ; Parse response body to check for error message
        body (-> response
                 :body
                 (hickory/parse)
                 (hickory/as-hickory))
        error-messages (select/select (select/class :error-message) body)]

    ; Should show validation error
    (is (= 200 (:status response)))
    (is (pos? (count error-messages)))))

(deftest test-post-reset-password-missing-fields
  (let [server (:myproject.server/server ig-extras/*test-system*)
        db (::db/db ig-extras/*test-system*)
        base-url (reitit-extras/get-server-url server :host)
        url (str base-url "/reset-password")
        test-email "user@example.com"
        original-password "original-password"
        
        ; Create a user for testing
        user (queries/create-user! db {:email test-email
                                       :password original-password})
        token (utils/create-test-token test-email (:id user))
        
        ; Submit password reset with missing password field
        response (http/post url {:cookies (utils/session-cookies
                                            {utils/CSRF-TOKEN-SESSION-KEY utils/TEST-CSRF-TOKEN})
                                 :form-params {utils/CSRF-TOKEN-FORM-KEY utils/TEST-CSRF-TOKEN
                                               :confirm-password "some-password"
                                               :token token}})
        
        ; Parse response body to check for error message
        body (-> response
                 :body
                 (hickory/parse)
                 (hickory/as-hickory))
        error-messages (select/select (select/class :error-message) body)]

    ; Should show validation error
    (is (= 200 (:status response)))
    (is (pos? (count error-messages)))))