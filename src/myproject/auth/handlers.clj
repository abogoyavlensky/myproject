(ns myproject.auth.handlers
  (:require [buddy.hashers :as hashers]
            [buddy.sign.jwt :as jwt]
            [myproject.auth.queries :as queries]
            [myproject.auth.views :as views]
            [myproject.routes :as-alias routes]
            [reitit-extras.core :as ext]
            [ring.util.response :as response])
  (:import [java.sql SQLException]
           [java.time Instant Duration]))

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
    (let [{:keys [email password]} (:form parameters)]
      (try
        (let [user (queries/create-user! (:db context) {:email email
                                                        :password password})]
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
                                                 :errors {:common ["unexpected server error"]}})))))))

(defn get-login
  [{router :reitit.core/router}]
  (let [page (views/login-page {:router router})]
    (ext/render-html page)))

(defn post-login
  [{:keys [errors params parameters context]
    router :reitit.core/router}]
  (if (some? errors)
    (ext/render-html (views/login-form {:router router
                                        :values params
                                        :errors (:humanized errors)}))
    (let [{:keys [email password]} (:form parameters)
          user (queries/get-user (:db context) email)
          ; Calculate password hash always to avoid timing attacks
          {:keys [valid]} (try
                            (hashers/verify password (:password user) {:alg :bcrypt+sha512})
                            (catch Exception _e
                              {:valid false})
                            (catch AssertionError _e
                              {:valid false}))]
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
  (ext/render-html (views/account-page {:user (:identity request)
                                        :router (:reitit.core/router request)})))

(defn post-change-password
  [{:keys [context errors parameters params identity]
    router :reitit.core/router
    :as request}]
  (if (seq errors)
    (ext/render-html (views/change-password-form {:user identity
                                                  :router router
                                                  :values params
                                                  :errors (:humanized errors)}))
    (let [{:keys [current-password new-password confirm-new-password]} (:form parameters)
          user (queries/get-user (:db context) (:email identity))
          {:keys [valid]} (hashers/verify current-password (:password user) {:alg :bcrypt+sha512})]
      (cond
        (not valid)
        (ext/render-html (views/change-password-form {:user identity
                                                      :router router
                                                      :values params
                                                      :errors {:current-password ["Current password is incorrect"]}}))

        (not= new-password confirm-new-password)
        (ext/render-html (views/change-password-form {:user identity
                                                      :router router
                                                      :values params
                                                      :errors {:common ["New passwords do not match"]}}))

        :else
        (let [password-hash (hashers/derive new-password {:alg :bcrypt+sha512})]
          (queries/update-password! (:db context) {:id (:id identity)
                                                   :password-hash password-hash})
          (ext/render-html (views/change-password-form {:user identity
                                                        :router router
                                                        :password-changed? true})))))))

(defn get-forgot-password
  [{router :reitit.core/router}]
  (let [page (views/forgot-password-page {:router router})]
    (ext/render-html page)))

(defn send-email!
  [{:keys [email reset-link]}]
  ; TODO: send email instead of printing to console
  (println (str "============================================\n"
                "Password Reset Link for: " email "\n"
                reset-link "\n"
                "============================================\n")))

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
        ; Generate JWT token for password reset
        (let [now (Instant/now)
              claims {:sub (:id user)
                      :email email
                      :exp (.getEpochSecond (.plus now (Duration/ofHours 24)))
                      :iat (.getEpochSecond now)}
              token (jwt/sign claims (:session-secret-key (:options context)) {:alg :hs256})
              reset-link (str (-> request :headers (get "host"))
                              (ext/get-route router ::routes/reset-password)
                              "?token=" token)]
          (send-email! {:email email
                        :reset-link reset-link})))
      (ext/render-html
        (views/forgot-password-form {:router router
                                     :email-sent? true})))))

(defn get-reset-password
  [{:keys [parameters context]
    router :reitit.core/router
    :as request}]
  (let [token (get-in parameters [:query :token])]
    (try
      (let [claims (jwt/unsign token (:session-secret-key (:options context)) {:alg :hs256})
            email (:email claims)]
        (ext/render-html (views/reset-password-page {:router router
                                                     :token token
                                                     :email email})))
      (catch Exception _e
        (-> (ext/render-html (views/invalid-reset-token-page {:router router}))
            (response/status 400))))))

(defn post-reset-password
  [{:keys [errors params parameters context]
    router :reitit.core/router
    :as request}]
  (if (seq errors)
    (ext/render-html (views/reset-password-form {:router router
                                                 :values (dissoc params :token)
                                                 :token (:token params)
                                                 :errors (:humanized errors)}))
    (let [{:keys [password confirm-password token]} (:form parameters)]
      (if (not= password confirm-password)
        (ext/render-html (views/reset-password-form {:router router
                                                     :values (dissoc params :token)
                                                     :token (:token params)
                                                     :errors {:common ["Passwords do not match"]}}))
        ; Verify the token and update the password
        (try
          (let [claims (jwt/unsign token (:session-secret-key (:options context)) {:alg :hs256})
                user-id (:sub claims)
                password-hash (hashers/derive password {:alg :bcrypt+sha512})]
            (queries/update-password! (:db context) {:id user-id
                                                     :password-hash password-hash})
            (ext/render-html (views/password-reset-success-page {:router router})))
          (catch Exception e
            (ext/render-html (views/reset-password-form {:router router
                                                         :values (dissoc params :token)
                                                         :token (:token params)
                                                         :errors {:common ["Invalid or expired token"]}}))))))))
