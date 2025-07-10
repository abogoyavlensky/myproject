(ns myproject.routes
  (:require [buddy.auth :as buddy-auth]
            [buddy.auth.backends :as backends]
            [buddy.auth.middleware :as auth-middleware]
            [myproject.auth.handlers :as auth-handlers]
            [myproject.auth.spec :as spec]
            [myproject.handlers :as handlers]
            [reitit-extras.core :as ext]
            [ring.util.response :as response]))

(defn wrap-login-required
  "Middleware used in routes that require authentication. Buddy checks
  if request key :identity is set to truthy value by any previous middleware.
  If the request is not authenticated, then redirect to Login page."
  [handler]
  (fn [{router :reitit.core/router
        :as request}]
    (if (buddy-auth/authenticated? request)
      (handler request)
      (response/redirect (ext/get-route router ::login)))))

(defn wrap-already-logged-in
  "Middleware used in routes that require authentication. Buddy checks
  if request key :identity is set to truthy value by any previous middleware.
  If the request is not authenticated, then redirect to Login page."
  [handler]
  (fn [{router :reitit.core/router
        :as request}]
    (if (buddy-auth/authenticated? request)
      (response/redirect (ext/get-route router ::home-page))
      (handler request))))

(def routes
  (let [auth-backend (backends/session)]
    [["/" {:name ::home-page
           :middleware [[auth-middleware/wrap-authentication auth-backend]]
           :get {:handler handlers/home-handler}
           :responses {200 {:body string?}}}]
     ["/health" {:name ::health-check
                 :get {:handler (fn [_] (response/response "OK"))}}]
     ["/register" {:name ::register
                   :middleware [[auth-middleware/wrap-authentication auth-backend]
                                wrap-already-logged-in]
                   :get {:handler auth-handlers/get-register}
                   :post {:handler auth-handlers/post-register
                          :parameters {:form [:map
                                              [:email spec/Email]
                                              [:password [:string {:min 8}]]]}
                          :responses {200 {:body string?}}}}]
     ["/login" {:name ::login
                :middleware [[auth-middleware/wrap-authentication auth-backend]
                             wrap-already-logged-in]
                :get {:handler auth-handlers/get-login}
                :post {:handler auth-handlers/post-login
                       :parameters {:form [:map
                                           [:email spec/Email]
                                           [:password [:string {:min 1}]]]}
                       :responses {200 {:body string?}}}}]
     ["/forgot-password" {:name ::forgot-password
                          :middleware [[auth-middleware/wrap-authentication auth-backend]
                                       wrap-already-logged-in]
                          :get {:handler auth-handlers/get-forgot-password}
                          :post {:handler auth-handlers/post-forgot-password
                                 :parameters {:form [:map
                                                     [:email spec/Email]]}
                                 :responses {200 {:body string?}}}}]
     ["/reset-password" {:name ::reset-password
                         :middleware [[auth-middleware/wrap-authentication auth-backend]
                                      wrap-already-logged-in]
                         :get {:handler auth-handlers/get-reset-password
                               :parameters {:query [:map
                                                    [:token string?]]}}
                         :post {:handler auth-handlers/post-reset-password
                                :parameters {:form [:map
                                                    [:password [:string {:min 8}]]
                                                    [:confirm-password [:string {:min 8}]]
                                                    [:token string?]]}
                                :responses {200 {:body string?}}}}]
     ["/logout" {:name ::logout
                 :post {:handler auth-handlers/post-logout}}]
     ["/account"
      ["" {:name ::account
           :middleware [[auth-middleware/wrap-authentication auth-backend]
                        wrap-login-required]
           :get {:handler auth-handlers/get-account
                 :responses {200 {:body string?}}}}]
      ["/change-password" {:name ::change-password
                           :middleware [[auth-middleware/wrap-authentication auth-backend]
                                        wrap-login-required]
                           :post {:handler auth-handlers/post-change-password
                                  :parameters {:form [:map
                                                      [:current-password [:string {:min 1}]]
                                                      [:new-password [:string {:min 8}]]
                                                      [:confirm-new-password [:string {:min 8}]]]}
                                  :responses {200 {:body string?}}}}]]]))
