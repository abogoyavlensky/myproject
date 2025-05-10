(ns myproject.routes
  (:require [myproject.handlers :as handlers]
            [ring.util.response :as response]
            [myproject.auth.handlers :as auth-handlers]))

(def routes
  [["/" {:name ::home-page
         :get {:handler handlers/home-handler}
         :responses {200 {:body string?}}}]
   ["/health" {:name ::health-check
               :get {:handler (fn [_] (response/response "OK"))}}]
   ["/register" {:name ::register
                 :get {:handler auth-handlers/register-handler}}]
   ["/login" {:name ::login
              :get {:handler auth-handlers/login-handler}}]])
