(ns myproject.auth-account-test
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

(deftest test-get-account-ok
  (let [server (:myproject.server/server ig-extras/*test-system*)
        db (::db/db ig-extras/*test-system*)
        base-url (reitit-extras/get-server-url server :host)

        ; Create a user for testing
        test-email "user@example.com"
        test-password "secure-password"
        user (queries/create-user! db {:email test-email
                                       :password test-password})
        account-url (str base-url "/account")
        response (http/get account-url {:cookies (utils/session-cookies {:identity user})})
        body (-> response
                 :body
                 (hickory/parse)
                 (hickory/as-hickory))]

    ; Check that account page loads correctly
    (is (= 200 (:status response)))

    ; Verify we have an account heading
    (is (= "Account settings"
           (->> body
                (select/select (select/tag :h2))
                (first)
                :content
                (first))))

    ; Verify the user email is displayed on the page
    (is (some? (->> body
                    (select/select (select/find-in-text #".*user@example.com.*"))
                    (first))))))

(deftest test-get-account-unauthenticated
  (let [server (:myproject.server/server ig-extras/*test-system*)
        base-url (reitit-extras/get-server-url server :host)
        account-url (str base-url "/account")
        ;; Try to access account page without authentication
        response (http/get account-url {:redirect-strategy :none})]

    ;; Should get a redirect to login page
    (is (= 302 (:status response)))
    (is (= "/login" (get-in response [:headers "Location"])))))

(deftest test-post-change-password-ok
  (let [server (:myproject.server/server ig-extras/*test-system*)
        db (::db/db ig-extras/*test-system*)
        base-url (reitit-extras/get-server-url server :host)

        ;; Create a user for testing
        test-email "password-change@example.com"
        original-password "original-password"
        new-password "new-secure-password"
        user (queries/create-user! db {:email test-email
                                       :password original-password})

        ; Submit password change
        change-password-url (str base-url "/account/change-password")
        change-response (http/post change-password-url
                                   {:cookies (utils/session-cookies
                                               {utils/CSRF-TOKEN-SESSION-KEY utils/TEST-CSRF-TOKEN
                                                :identity user})
                                    :form-params {utils/CSRF-TOKEN-FORM-KEY utils/TEST-CSRF-TOKEN
                                                  :current-password original-password
                                                  :new-password new-password
                                                  :confirm-new-password new-password}})

        ; Parse response to check for success message
        change-body (-> change-response
                        :body
                        (hickory/parse)
                        (hickory/as-hickory))]

    ; Verify password change was successful
    (is (= 200 (:status change-response)))

    ; Check for success message in the response
    (is (some? (->> change-body
                    (select/select (select/find-in-text #".*Password Updated Successfully.*"))
                    (first))))))

(deftest test-get-account-form-structure
  (let [server (:myproject.server/server ig-extras/*test-system*)
        db (::db/db ig-extras/*test-system*)
        base-url (reitit-extras/get-server-url server :host)

        ; Create a user for testing
        test-email "user@example.com"
        test-password "secure-password"
        user (queries/create-user! db {:email test-email
                                       :password test-password})
        account-url (str base-url "/account")
        response (http/get account-url {:cookies (utils/session-cookies {:identity user})})
        body (-> response
                 :body
                 (hickory/parse)
                 (hickory/as-hickory))]

    ; Check form structure
    (is (= 200 (:status response)))

    ; Verify change password form exists
    (let [form (->> body
                    (select/select (select/tag :form))
                    (filter #(= "/account/change-password" (get-in % [:attrs :hx-post])))
                    (first))]
      (is (some? form))
      (is (= "form-change-password" (get-in form [:attrs :id]))))

    ; Verify form has required fields
    (let [inputs (->> body
                      (select/select (select/tag :input))
                      (map (comp :name :attrs))
                      (set))]
      (is (contains? inputs "current-password"))
      (is (contains? inputs "new-password"))
      (is (contains? inputs "confirm-new-password"))
      (is (contains? inputs (name utils/CSRF-TOKEN-FORM-KEY))))))

(deftest test-post-change-password-wrong-current-password
  (let [server (:myproject.server/server ig-extras/*test-system*)
        db (::db/db ig-extras/*test-system*)
        base-url (reitit-extras/get-server-url server :host)

        ; Create a user for testing
        test-email "user@example.com"
        correct-password "correct-password"
        wrong-password "wrong-password"
        new-password "new-secure-password"
        user (queries/create-user! db {:email test-email
                                       :password correct-password})

        ; Submit password change with wrong current password
        change-password-url (str base-url "/account/change-password")
        response (http/post change-password-url
                            {:cookies (utils/session-cookies
                                        {utils/CSRF-TOKEN-SESSION-KEY utils/TEST-CSRF-TOKEN
                                         :identity user})
                             :form-params {utils/CSRF-TOKEN-FORM-KEY utils/TEST-CSRF-TOKEN
                                           :current-password wrong-password
                                           :new-password new-password
                                           :confirm-new-password new-password}})

        ; Parse response to check for error message
        body (-> response
                 :body
                 (hickory/parse)
                 (hickory/as-hickory))
        error-messages (select/select (select/class :error-message) body)]

    ; Verify error response
    (is (= 200 (:status response)))
    (is (= 1 (count error-messages)))
    (is (= ["Current password is incorrect"] (-> error-messages first :content)))))

(deftest test-post-change-password-mismatch
  (let [server (:myproject.server/server ig-extras/*test-system*)
        db (::db/db ig-extras/*test-system*)
        base-url (reitit-extras/get-server-url server :host)

        ; Create a user for testing
        test-email "user@example.com"
        current-password "current-password"
        new-password "new-secure-password"
        different-password "different-password"
        user (queries/create-user! db {:email test-email
                                       :password current-password})

        ; Submit password change with mismatched passwords
        change-password-url (str base-url "/account/change-password")
        response (http/post change-password-url
                            {:cookies (utils/session-cookies
                                        {utils/CSRF-TOKEN-SESSION-KEY utils/TEST-CSRF-TOKEN
                                         :identity user})
                             :form-params {utils/CSRF-TOKEN-FORM-KEY utils/TEST-CSRF-TOKEN
                                           :current-password current-password
                                           :new-password new-password
                                           :confirm-new-password different-password}})

        ; Parse response to check for error message
        body (-> response
                 :body
                 (hickory/parse)
                 (hickory/as-hickory))
        error-messages (select/select (select/class :error-message) body)]

    ; Verify error response
    (is (= 200 (:status response)))
    (is (= 1 (count error-messages)))
    (is (= ["New passwords do not match"] (-> error-messages first :content)))))

(deftest test-post-change-password-missing-fields
  (let [server (:myproject.server/server ig-extras/*test-system*)
        db (::db/db ig-extras/*test-system*)
        base-url (reitit-extras/get-server-url server :host)

        ; Create a user for testing
        test-email "user@example.com"
        current-password "current-password"
        user (queries/create-user! db {:email test-email
                                       :password current-password})

        ; Submit password change with missing new password
        change-password-url (str base-url "/account/change-password")
        response (http/post change-password-url
                            {:cookies (utils/session-cookies
                                        {utils/CSRF-TOKEN-SESSION-KEY utils/TEST-CSRF-TOKEN
                                         :identity user})
                             :form-params {utils/CSRF-TOKEN-FORM-KEY utils/TEST-CSRF-TOKEN
                                           :current-password current-password
                                           :confirm-new-password "some-password"}})

        ; Parse response to check for error message
        body (-> response
                 :body
                 (hickory/parse)
                 (hickory/as-hickory))
        error-messages (select/select (select/class :error-message) body)]

    ; Verify error response
    (is (= 200 (:status response)))
    (is (pos? (count error-messages)))))

(deftest test-post-change-password-too-short
  (let [server (:myproject.server/server ig-extras/*test-system*)
        db (::db/db ig-extras/*test-system*)
        base-url (reitit-extras/get-server-url server :host)

        ; Create a user for testing
        test-email "user@example.com"
        current-password "current-password"
        short-password "123"
        user (queries/create-user! db {:email test-email
                                       :password current-password})

        ; Submit password change with too short password
        change-password-url (str base-url "/account/change-password")
        response (http/post change-password-url
                            {:cookies (utils/session-cookies
                                        {utils/CSRF-TOKEN-SESSION-KEY utils/TEST-CSRF-TOKEN
                                         :identity user})
                             :form-params {utils/CSRF-TOKEN-FORM-KEY utils/TEST-CSRF-TOKEN
                                           :current-password current-password
                                           :new-password short-password
                                           :confirm-new-password short-password}})

        ; Parse response to check for error message
        body (-> response
                 :body
                 (hickory/parse)
                 (hickory/as-hickory))
        error-messages (select/select (select/class :error-message) body)]

    ; Verify error response
    (is (= 200 (:status response)))
    (is (pos? (count error-messages)))))

(deftest test-post-change-password-unauthenticated
  (let [server (:myproject.server/server ig-extras/*test-system*)
        base-url (reitit-extras/get-server-url server :host)
        change-password-url (str base-url "/account/change-password")

        ; Try to change password without authentication
        response (http/post change-password-url
                            {:redirect-strategy :none
                             :cookies (utils/session-cookies
                                        {utils/CSRF-TOKEN-SESSION-KEY utils/TEST-CSRF-TOKEN})
                             :form-params {utils/CSRF-TOKEN-FORM-KEY utils/TEST-CSRF-TOKEN
                                           :current-password "current"
                                           :new-password "new"
                                           :confirm-new-password "new"}})]

    ; Should get a redirect to login page
    (is (= 302 (:status response)))
    (is (= "/login" (get-in response [:headers "Location"])))))
