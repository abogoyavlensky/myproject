(ns myproject.auth.handlers
  (:require [buddy.hashers :as hashers]
            [myproject.auth.queries :as queries]
            [myproject.auth.views :as views]
            [reitit-extras.core :as ext]
            [myproject.routes :as-alias routes]
            [ring.util.response :as response])
  (:import [java.sql SQLException]))

(defn get-register
  [{router :reitit.core/router}]
  (let [page (views/register-page {:router router})]
    (ext/render-html page)))

(defn post-register
  [{:keys [context errors parameters params]
    router :reitit.core/router}]
  (if (some? errors)
    (ext/render-html (views/register-form {:router router
                                           :values params
                                           :errors (:humanized errors)}))
    (let [{:keys [email password]} (:form parameters)
          password-hash (hashers/derive password {:alg :bcrypt+sha512})]
      (try
        (let [user (queries/create-user! (:db context) {:email email
                                                        :password-hash password-hash})]
          (-> (ext/render-html [:div])
              (response/header "HX-Redirect" "/")
              (assoc :session {:identity (dissoc user :password)})))
        ; TODO: refactor this to use a common error handler
        (catch SQLException e
          (if (re-find #"UNIQUE constraint failed" (ex-message e))
            (ext/render-html (views/register-form {:router router
                                                   :values params
                                                   :errors {:email ["user already exists"]}}))
            (ext/render-html (views/register-form {:router router
                                                   :values params
                                                   :errors {:email ["unexpected database error while creating account"]}}))))
        (catch Exception _e
          (ext/render-html (views/register-form {:router router
                                                 :values params
                                                 :errors {:email ["unexpected server error"]}})))))))


(defn get-login
  [{router :reitit.core/router}]
  (let [page (views/login-page {:router router})]
    (ext/render-html page)))

(defn post-login
  [{:keys [errors params parameters context]
    router :reitit.core/router
    :as request}]
  (if (some? errors)
    (ext/render-html (views/login-form {:router router
                                        :values params
                                        :errors (:humanized errors)}))
    (let [{:keys [email password]} (:form parameters)
          user (queries/get-user (:db context) email)
          ; Calculate password hash always to avoid timing attacks
          {:keys [valid]} (hashers/verify password (:password user) {:alg :bcrypt+sha512})]
      (if (and (some? user) valid)
        (-> (ext/render-html [:div])
            (response/header "HX-Redirect" "/")
            (assoc :session {:identity (dissoc user :password)}))
        (ext/render-html (views/login-form {:router router
                                            :values params
                                            :errors {:common ["Invalid email or password"]}}))))))

(defn post-logout
  [{:keys [errors params parameters context]
    router :reitit.core/router
    :as request}]
  (-> (ext/render-html [:div])
      (response/header "HX-Redirect" "/")
      (assoc :session nil)))

(defn get-account
  [request]
  (ext/render-html (views/account-page {:user (:identity request)})))

(defn get-forgot-password
  [{router :reitit.core/router}]
  (let [page (views/forgot-password-page {:router router})]
    (ext/render-html page)))

(defn post-forgot-password
  [{:keys [errors params parameters context]
    router :reitit.core/router
    :as request}]
  (if (seq errors)
    (ext/render-html (views/forgot-password-form {:router router
                                                  :values params
                                                  :errors (:humanized errors)}))
    (let [{:keys [email]} (:form parameters)
          user (queries/get-user (:db context) email)]
      (when (some? user)
        ; TODO: send email with reset link
        (println (str "============================================\n"
                      "Sending password reset email to: " email "\n"
                      "============================================\n")))
      (ext/render-html
        (views/forgot-password-form {:router router
                                     :email-sent? true})))))
