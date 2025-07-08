(ns myproject.auth-forgot-password-test
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

(deftest test-get-forgot-password-ok
  (let [server (:myproject.server/server ig-extras/*test-system*)
        base-url (reitit-extras/get-server-url server :host)
        url (str base-url "/forgot-password")
        body (-> (http/get url)
                 :body
                 (hickory/parse)
                 (hickory/as-hickory))]

    (testing "Check page title and form structure"
      (is (= "Forgot your password?"
             (->> body
                  (select/select (select/tag :h2))
                  (first)
                  :content
                  (first)))))

    (testing "Check form has required fields"
      (is (= #{(name utils/CSRF-TOKEN-FORM-KEY) "email"}
             (->> body
                  (select/select (select/tag :input))
                  (map (comp :name :attrs))
                  (set)))))

    (testing "Check form properties"
      (is (= {:hx-post "/forgot-password"
              :hx-target "#form-forgot-password"
              :id "form-forgot-password"}
             (dissoc (->> body (select/select (select/tag :form)) first :attrs)
                     :class :hx-swap))))))

(deftest test-get-forgot-password-already-logged-in
  (let [server (:myproject.server/server ig-extras/*test-system*)
        db (::db/db ig-extras/*test-system*)
        base-url (reitit-extras/get-server-url server :host)
        url (str base-url "/forgot-password")
        test-email "user@example.com"
        test-password "password123"
        user (queries/create-user! db {:email test-email
                                       :password test-password})
        response (http/get url {:redirect-strategy :none
                                :cookies (utils/session-cookies {:identity user})})]

    (testing "Should get a redirect to home page"
      (is (= 302 (:status response)))
      (is (= "/" (get-in response [:headers "Location"]))))))

(deftest test-post-forgot-password-valid-email
  (let [server (:myproject.server/server ig-extras/*test-system*)
        db (::db/db ig-extras/*test-system*)
        base-url (reitit-extras/get-server-url server :host)
        url (str base-url "/forgot-password")
        test-email "user@example.com"
        test-password "password123"
        _ (queries/create-user! db {:email test-email
                                    :password test-password})
        response (http/post url {:cookies (utils/session-cookies
                                            {utils/CSRF-TOKEN-SESSION-KEY utils/TEST-CSRF-TOKEN})
                                 :form-params {utils/CSRF-TOKEN-FORM-KEY utils/TEST-CSRF-TOKEN
                                               :email test-email}})
        body (-> response
                 :body
                 (hickory/parse)
                 (hickory/as-hickory))]

    (testing "Should show success message regardless of user existence"
      (is (= 200 (:status response)))
      (is (some? (->> body
                      (select/select (select/find-in-text #"Check your email.*"))
                      (first)))))))

(deftest test-post-forgot-password-nonexistent-email
  (let [server (:myproject.server/server ig-extras/*test-system*)
        base-url (reitit-extras/get-server-url server :host)
        url (str base-url "/forgot-password")
        nonexistent-email "nonexistent@example.com"
        response (http/post url {:cookies (utils/session-cookies
                                            {utils/CSRF-TOKEN-SESSION-KEY utils/TEST-CSRF-TOKEN})
                                 :form-params {utils/CSRF-TOKEN-FORM-KEY utils/TEST-CSRF-TOKEN
                                               :email nonexistent-email}})
        body (-> response
                 :body
                 (hickory/parse)
                 (hickory/as-hickory))]

    ; Should still show success message (security best practice)
    (is (= 200 (:status response)))
    (is (some? (->> body
                    (select/select (select/find-in-text #"Check your email.*"))
                    (first))))))

(deftest test-post-forgot-password-invalid-email
  (let [server (:myproject.server/server ig-extras/*test-system*)
        base-url (reitit-extras/get-server-url server :host)
        url (str base-url "/forgot-password")
        invalid-email "not-an-email"

        ; Try to submit with invalid email format
        response (http/post url {:cookies (utils/session-cookies
                                            {utils/CSRF-TOKEN-SESSION-KEY utils/TEST-CSRF-TOKEN})
                                 :form-params {utils/CSRF-TOKEN-FORM-KEY utils/TEST-CSRF-TOKEN
                                               :email invalid-email}})

        ; Parse response body to check for error message
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

(deftest test-post-forgot-password-missing-email
  (let [server (:myproject.server/server ig-extras/*test-system*)
        base-url (reitit-extras/get-server-url server :host)
        url (str base-url "/forgot-password")

        ; Try to submit without email
        response (http/post url {:cookies (utils/session-cookies
                                            {utils/CSRF-TOKEN-SESSION-KEY utils/TEST-CSRF-TOKEN})
                                 :form-params {utils/CSRF-TOKEN-FORM-KEY utils/TEST-CSRF-TOKEN}})

        ; Parse response body to check for error message
        body (-> response
                 :body
                 (hickory/parse)
                 (hickory/as-hickory))
        error-messages (select/select (select/class :error-message) body)]

    (is (= 200 (:status response)))
    (is (pos? (count error-messages)))))

(deftest test-post-forgot-password-empty-email
  (let [server (:myproject.server/server ig-extras/*test-system*)
        base-url (reitit-extras/get-server-url server :host)
        url (str base-url "/forgot-password")

        ; Try to submit with empty email
        response (http/post url {:cookies (utils/session-cookies
                                            {utils/CSRF-TOKEN-SESSION-KEY utils/TEST-CSRF-TOKEN})
                                 :form-params {utils/CSRF-TOKEN-FORM-KEY utils/TEST-CSRF-TOKEN
                                               :email ""}})

        ; Parse response body to check for error message
        body (-> response
                 :body
                 (hickory/parse)
                 (hickory/as-hickory))
        error-messages (select/select (select/class :error-message) body)]

    (is (= 200 (:status response)))
    (is (pos? (count error-messages)))))
