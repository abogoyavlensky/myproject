(ns myproject.auth-logout-test
  (:require [clj-http.client :as http]
            [clojure.test :refer :all]
            [integrant-extras.tests :as ig-extras]
            [myproject.auth.queries :as queries]
            [myproject.test-utils :as utils]
            [reitit-extras.tests :as reitit-extras]))

(use-fixtures :once
  (ig-extras/with-system))

(use-fixtures :each
  utils/with-truncated-tables)

(deftest test-post-logout
  (let [base-url (reitit-extras/get-server-url (utils/server))
        logout-url (str base-url "/auth/logout")
        user (queries/create-user! (utils/db) {:email "user@example.com"
                                               :password "password123"})
        response (http/post logout-url {:cookies (utils/session-cookies
                                                   {utils/CSRF-TOKEN-SESSION-KEY utils/TEST-CSRF-TOKEN
                                                    :identity user})
                                        :form-params {utils/CSRF-TOKEN-FORM-KEY utils/TEST-CSRF-TOKEN}})]
    (is (= 200 (:status response)))
    (is (= "/" (get (:headers response) "HX-Redirect")))))

(deftest test-post-logout-unauthenticated
  (let [base-url (reitit-extras/get-server-url (utils/server))
        logout-url (str base-url "/auth/logout")
        response (http/post logout-url {:cookies (utils/session-cookies
                                                   {utils/CSRF-TOKEN-SESSION-KEY utils/TEST-CSRF-TOKEN})
                                        :form-params {utils/CSRF-TOKEN-FORM-KEY utils/TEST-CSRF-TOKEN}})]
    (is (= 200 (:status response)))
    (is (= "/" (get (:headers response) "HX-Redirect")))))
