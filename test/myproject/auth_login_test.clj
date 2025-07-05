(ns myproject.auth-login-test
  (:require [clj-http.client :as http]
            [clojure.test :refer :all]
            [hickory.core :as hickory]
            [hickory.select :as select]
            [integrant-extras.tests :as ig-extras]
            [myproject.auth.queries :as queries]
            [myproject.db :as db]
            [myproject.test-utils :as test-utils]
            [reitit-extras.tests :as reitit-extras]))

(use-fixtures :once
              (ig-extras/with-system))

(use-fixtures :each
              ;test-utils/with-mock-csrf-token
              test-utils/with-truncated-tables)

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
    (is (= #{(name test-utils/CSRF-TOKEN-KEY) "email" "password"}
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

        ;; Now attempt to login using simplified CSRF handling
        response (test-utils/post-with-csrf login-url
                                            {:email test-email
                                             :password test-password})]

    (is (= 200 (:status response)))
    (is (= "/" (get (:headers response) "HX-Redirect")))))

(deftest test-post-login-invalid-email
  (let [server (:myproject.server/server ig-extras/*test-system*)
        base-url (reitit-extras/get-server-url server :host)
        url (str base-url "/login")
        invalid-email "not-an-email"
        
        ;; Try to login with an invalid email format
        ;response (test-utils/post-with-csrf url {:email invalid-email
        ;                                         :password "some-password"})

        response (test-utils/post-with-custom-session
                   url
                   {:email invalid-email
                    :password "some-password"})
        
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

(deftest test-post-login-incorrect-password
  (let [server (:myproject.server/server ig-extras/*test-system*)
        db (::db/db ig-extras/*test-system*)
        base-url (reitit-extras/get-server-url server :host)
        register-url (str base-url "/register")
        login-url (str base-url "/login")
        test-email "user2@example.com"
        correct-password "password123"
        incorrect-password "wrong-password"
        
        ;; First register a user
        {:keys [csrf-token cookies]} (test-utils/get-csrf-token-and-cookies register-url)
        _ (http/post register-url {:cookies cookies
                                   :form-params {test-utils/CSRF-TOKEN-KEY csrf-token
                                                 :email test-email
                                                 :password correct-password}})

        ;; Now attempt to login with incorrect password
        {:keys [csrf-token cookies]} (test-utils/get-csrf-token-and-cookies login-url)
        response (http/post login-url {:cookies cookies
                                       :form-params {test-utils/CSRF-TOKEN-KEY csrf-token
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
        {:keys [csrf-token cookies]} (test-utils/get-csrf-token-and-cookies login-url)
        response (http/post login-url {:cookies cookies
                                       :form-params {test-utils/CSRF-TOKEN-KEY csrf-token
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
