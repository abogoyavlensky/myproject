(ns myproject.routes
  (:require [myproject.auth.handlers :as auth-handlers]
            [myproject.handlers :as handlers]
            [buddy.auth :as buddy-auth]
            [buddy.auth.backends :as backends]
            [buddy.auth.middleware :as auth-middleware]
            [reitit-extras.core :as ext]
            [ring.util.response :as response]))

(defn wrap-authenticated?
  "Middleware used in routes that require authentication. Buddy checks
  if request key :identity is set to truthy value by any previous middleware.
  If the request is not authenticated, then redirect to Login page."
  [handler]
  (fn [{router :reitit.core/router
        :as request}]
    (if (buddy-auth/authenticated? request)
      (handler request)
      (response/redirect (ext/get-route router ::login)))))

(def routes
  (let [auth-backend (backends/session)]
    [["/" {:name ::home-page
           :middleware [[auth-middleware/wrap-authentication auth-backend]]
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
                :get {:handler auth-handlers/get-login}
                :post {:handler auth-handlers/post-login
                       :parameters {:form [:map
                                           [:email [:string {:min 1}]]
                                           [:password [:string {:min 1}]]]}
                       :responses {200 {:body string?}}}}]
     ["/logout" {:name ::logout
                 :post {:handler auth-handlers/post-logout}}]
     ["/account" {:name ::account
                  :middleware [[auth-middleware/wrap-authentication auth-backend]
                               wrap-authenticated?]
                  :get {:handler auth-handlers/get-account
                        :responses {200 {:body string?}}}}]]))
