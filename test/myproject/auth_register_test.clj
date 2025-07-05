(ns myproject.auth-register-test
  (:require [buddy.hashers :as hashers]
            [clj-http.client :as http]
            [clojure.test :refer :all]
            [hickory.core :as hickory]
            [hickory.select :as select]
            [integrant-extras.tests :as ig-extras]
            [myproject.db :as db]
            [myproject.server :as-alias server]
            [myproject.test-utils :as test-utils]
            [myproject.test-utils :as utils]
            [reitit-extras.tests :as reitit-extras]))

(use-fixtures :once
  (ig-extras/with-system))

(use-fixtures :each
  test-utils/with-truncated-tables)

(deftest test-get-register-ok
  (let [server (::server/server ig-extras/*test-system*)
        base-url (reitit-extras/get-server-url server :host)
        url (str base-url "/register")
        body (-> (http/get url)
                 :body
                 (hickory/parse)
                 (hickory/as-hickory))]
    (is (= "Register"
           (->> body
                (select/select (select/tag :h2))
                (first)
                :content
                (first))))
    (is (= #{"__anti-forgery-token" "email" "password"}
           (->> body
                (select/select (select/tag :input))
                (map (comp :name :attrs))
                (set))))
    (is (= {:hx-post "/register"
            :hx-target "#form-register"
            :id "form-register"}
           (dissoc (->> body (select/select (select/tag :form)) first :attrs)
                   :class :hx-swap)))))

(deftest test-post-register-ok
  (let [server (::server/server ig-extras/*test-system*)
        db (::db/db ig-extras/*test-system*)
        base-url (reitit-extras/get-server-url server :host)
        url (str base-url "/register")
        response (http/post url {:cookies (utils/session-cookies
                                            {utils/CSRF-TOKEN-SESSION-KEY utils/TEST-CSRF-TOKEN})
                                 :form-params {test-utils/CSRF-TOKEN-FORM-KEY utils/TEST-CSRF-TOKEN
                                               :email "user@gmail.com"
                                               :password "secret-password"}})
        user (db/exec-one! db {:select [:email :password]
                               :from [:user]})]

    (is (= "user@gmail.com" (:email user)))
    (is (true? (:valid (hashers/verify "secret-password" (:password user) {:alg :bcrypt+sha512}))))
    (is (= 200 (:status response)))
    (is (= "/" (get (:headers response) "HX-Redirect")))))

(deftest test-post-register-user-already-exists
  (let [server (::server/server ig-extras/*test-system*)
        base-url (reitit-extras/get-server-url server :host)
        url (str base-url "/register")
        test-email "existing@gmail.com"

        ; First, register a user to create the existing account
        _ (http/post url {:cookies (utils/session-cookies
                                     {utils/CSRF-TOKEN-SESSION-KEY utils/TEST-CSRF-TOKEN})
                          :form-params {test-utils/CSRF-TOKEN-FORM-KEY utils/TEST-CSRF-TOKEN
                                        :email test-email
                                        :password "first-password"}})

        ; Now try to register again with the same email
        response (http/post url {:cookies (utils/session-cookies
                                            {utils/CSRF-TOKEN-SESSION-KEY utils/TEST-CSRF-TOKEN})
                                 :form-params {test-utils/CSRF-TOKEN-FORM-KEY utils/TEST-CSRF-TOKEN
                                               :email test-email
                                               :password "second-password"}})

        ;; Parse the response body to check for error message
        body (-> response
                 :body
                 (hickory/parse)
                 (hickory/as-hickory))
        error-messages (select/select (select/class :error-message) body)
        inputs (select/select (select/tag :input) body)]

    (is (= 1 (count error-messages)))
    (is (= 200 (:status response)))
    (is (= ["User already exists"] (-> error-messages first :content)))
    (is (= test-email (->> inputs
                           (filter #(= "email" (get-in % [:attrs :name])))
                           first
                           :attrs
                           :value)))))

(deftest test-post-register-invalid-email
  (let [server (::server/server ig-extras/*test-system*)
        base-url (reitit-extras/get-server-url server :host)
        url (str base-url "/register")
        invalid-email "not-an-email"

        ;; Try to register with an invalid email format
        response (http/post url {:cookies (utils/session-cookies
                                            {utils/CSRF-TOKEN-SESSION-KEY utils/TEST-CSRF-TOKEN})
                                 :form-params {test-utils/CSRF-TOKEN-FORM-KEY utils/TEST-CSRF-TOKEN
                                               :email invalid-email
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
    (is (= ["Invalid email format"] (-> error-messages first :content)))
    (is (= invalid-email (->> inputs
                              (filter #(= "email" (get-in % [:attrs :name])))
                              first
                              :attrs
                              :value)))))

(deftest test-post-register-password-too-short
  (let [server (::server/server ig-extras/*test-system*)
        base-url (reitit-extras/get-server-url server :host)
        url (str base-url "/register")
        test-email "test@example.com"
        short-password "1234567" ; Less than 8 characters

        ;; Try to register with a password that's too short
        response (http/post url {:cookies (utils/session-cookies
                                            {utils/CSRF-TOKEN-SESSION-KEY utils/TEST-CSRF-TOKEN})
                                 :form-params {test-utils/CSRF-TOKEN-FORM-KEY utils/TEST-CSRF-TOKEN
                                               :email test-email
                                               :password short-password}})

        ;; Parse the response body to check for error message
        body (-> response
                 :body
                 (hickory/parse)
                 (hickory/as-hickory))
        error-messages (select/select (select/class :error-message) body)
        inputs (select/select (select/tag :input) body)]

    (is (= 1 (count error-messages)))
    (is (= 200 (:status response)))
    (is (= ["Should be at least 8 characters"] (-> error-messages first :content)))
    (is (= test-email (->> inputs
                           (filter #(= "email" (get-in % [:attrs :name])))
                           first
                           :attrs
                           :value)))))
