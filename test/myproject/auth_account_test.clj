(ns myproject.auth-account-test
  (:require [clj-http.client :as http]
            [clojure.test :refer :all]
            [hickory.core :as hickory]
            [hickory.select :as select]
            [myproject.auth.queries :as queries]
            [integrant-extras.tests :as ig-extras]
            [myproject.db :as db]
            [myproject.server :as-alias server]
            [myproject.test-utils :as test-utils]
            [reitit-extras.tests :as reitit-extras]))

(use-fixtures :once
  (ig-extras/with-system))

(use-fixtures :each
  test-utils/with-truncated-tables)

(deftest test-get-account-ok
  (let [server (::server/server ig-extras/*test-system*)
        db (::db/db ig-extras/*test-system*)
        base-url (reitit-extras/get-server-url server :host)

        ;; Create a user for testing
        test-email "user@example.com"
        test-password "secure-password"
        _ (queries/create-user! db {:email test-email
                                    :password test-password})

        ;; Now access the account page with the authenticated session
        {:keys [cookies]} (test-utils/get-logged-in-cookies base-url
                                                            {:email test-email
                                                             :password test-password})
        account-url (str base-url "/account")
        account-response (http/get account-url {:cookies cookies})
        body (-> account-response
                 :body
                 (hickory/parse)
                 (hickory/as-hickory))]

    ;; Check that account page loads correctly
    (is (= 200 (:status account-response)))
    
    ;; Verify we have an account heading
    (is (= "Account settings"
           (->> body
                (select/select (select/tag :h2))
                (first)
                :content
                (first))))
    
    ;; Verify the user email is displayed on the page
    (is (some? (->> body
                    (select/select (select/find-in-text #".*user@example.com.*"))
                    (first))))))

(deftest test-get-account-unauthenticated
  (let [server (::server/server ig-extras/*test-system*)
        base-url (reitit-extras/get-server-url server :host)
        account-url (str base-url "/account")
        ;; Try to access account page without authentication
        response (http/get account-url {:redirect-strategy :none})]
    
    ;; Should get a redirect to login page
    (is (= 302 (:status response)))
    (is (= "/login" (get-in response [:headers "Location"])))))


