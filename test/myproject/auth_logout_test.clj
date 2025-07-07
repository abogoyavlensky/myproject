(ns myproject.auth-logout-test
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
