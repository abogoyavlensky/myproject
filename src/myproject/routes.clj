(ns myproject.routes
  (:require [myproject.auth.handlers :as auth-handlers]
            [myproject.handlers :as handlers]
            [ring.util.response :as response]))

(def routes
  [["/" {:name ::home-page
         :get {:handler handlers/home-handler}
         :responses {200 {:body string?}}}]
   ["/health" {:name ::health-check
               :get {:handler (fn [_] (response/response "OK"))}}]
   ["/register" {:name ::register
                 :get {:handler auth-handlers/get-register}
                 :post {:handler auth-handlers/post-register
                        :parameters {:form [:map
                                            [:email [:string {:min 1}]]
                                            [:password [:string {:min 1}]]]}
                        :responses {200 {:body string?}}}}]
   ["/login" {:name ::login
              :get {:handler auth-handlers/get-login}}]])
