(ns myproject.auth-account-change-password-test
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
