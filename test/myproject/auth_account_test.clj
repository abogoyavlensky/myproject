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
