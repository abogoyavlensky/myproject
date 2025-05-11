(ns myproject.auth.handlers
  (:require [buddy.hashers :as hashers]
            [myproject.auth.queries :as queries]
            [myproject.auth.views :as views]
            [reitit-extras.core :as ext]
            [ring.util.response :as response])
  (:import [java.sql SQLException]))

(defn get-register
  [{router :reitit.core/router}]
  (let [page (views/register-page {:router router})]
    (ext/render-html page)))

(defn post-register
  [{:keys [context errors parameters params]
    :as request
    router :reitit.core/router}]
  (if (some? errors)
    (ext/render-html (views/register-form {:router router
                                           :values params
                                           :errors (:humanized errors)}))
    ; Calculate password hash always to avoid timing attacks
    (let [{:keys [email password]} (:form parameters)
          password-hash (hashers/derive password {:alg :bcrypt+sha512})]
      (try
        (queries/create-user! (:db context) {:email email
                                             :password-hash password-hash})
        (-> (ext/render-html [:div])
            (response/header "HX-Redirect" "/"))
        ; TODO: setup user to session
        ; TODO: refactor this to use a common error handler
        (catch SQLException e
          (if (re-find #"UNIQUE constraint failed" (ex-message e))
            (-> (ext/render-html (views/register-form {:router router
                                                       :values params
                                                       :errors {:email ["user already exists"]}}))
                (assoc :status 400))
            (ext/render-html (views/register-form {:router router
                                                   :values params
                                                   :errors {:email ["unexpected database error while creating account"]}}))))
        (catch Exception _e
          (ext/render-html (views/register-form {:router router
                                                 :values params
                                                 :errors {:email ["unexpected server error"]}})))))))


(defn get-login
  [_]
  (let [page (views/login-page)]
    (ext/render-html page)))
