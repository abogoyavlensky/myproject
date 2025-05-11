(ns myproject.auth.handlers
  (:require [buddy.hashers :as hashers]
            [myproject.auth.queries :as queries]
            [myproject.auth.views :as views]
            [reitit-extras.core :as ext]
            [ring.util.response :as response]))

(defn get-register
  [{router :reitit.core/router}]
  (let [page (views/register-page {:router router})]
    (ext/render-html page)))

(defn post-register
  [{:keys [context errors parameters params]
    :as request
    router :reitit.core/router}]
  #p (keys request)
  #p parameters
  #p (:errors request)

  (let [email (:email parameters)
        password (:password parameters)
        ; Calculate password hash always to avoid timing attacks
        password-hash #p (hashers/derive password {:alg :bcrypt+sha512})]
    (if (some? errors)
      (ext/render-html (views/register-form #p {:router router
                                                :values params
                                                :errors (:humanized errors)}))
      (do
        ;(queries/create-user! (:db context) {:email email
        ;                                     :password-hash password-hash})
        (ext/render-html (views/register-form {:router router}))))))


(defn get-login
  [_]
  (let [page (views/login-page)]
    (ext/render-html page)))
