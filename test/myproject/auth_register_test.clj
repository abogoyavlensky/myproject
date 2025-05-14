(ns myproject.auth-register-test
  (:require [clj-http.client :as http]
            [clojure.test :refer :all]
            [buddy.hashers :as hashers]
            [hickory.core :as hickory]
            [hickory.select :as select]
            [integrant-extras.tests :as ig-extras]
            [myproject.db :as db]
            [myproject.server :as-alias server]
            [myproject.test-utils :as test-utils]
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
        {:keys [csrf-token cookies]} (test-utils/get-csrf-token-and-cookies url)
        response (http/post url {:cookies cookies
                                 :form-params {test-utils/CSRF-TOKEN-KEY csrf-token
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

        ;; First, register a user to create the existing account
        {:keys [csrf-token cookies]} (test-utils/get-csrf-token-and-cookies url)
        _ (http/post url {:cookies cookies
                          :form-params {test-utils/CSRF-TOKEN-KEY csrf-token
                                        :email test-email
                                        :password "first-password"}})

        ;; Now try to register again with the same email
        {:keys [csrf-token cookies]} (test-utils/get-csrf-token-and-cookies url)
        response (http/post url {:cookies cookies
                                 :form-params {test-utils/CSRF-TOKEN-KEY csrf-token
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
