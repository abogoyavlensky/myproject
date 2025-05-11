(ns myproject.auth.handlers
  (:require [myproject.auth.views :as views]
            [reitit-extras.core :as reitit-extras]
            [ring.util.response :as response]))

(defn get-register
  [{router :reitit.core/router}]
  (let [page (views/register-page {:router router})]
    (reitit-extras/render-html page)))

(defn post-register
  [{:keys [context params]}]
  (let [email (:email params)
        password (:password params)]
    (if (and email password)
      (response/response "Login successful")
      (response/response "Invalid credentials"))))

(defn get-login
  [_]
  (let [page (views/login-page)]
    (reitit-extras/render-html page)))