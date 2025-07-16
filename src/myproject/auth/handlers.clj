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

(def ^:const PASSWORD-HASH-ALGORITHM :bcrypt+sha512)
(def ^:const JWT-ALGORITHM :hs256)

; Common response utilities
(defn redirect-response
  "Create an HTMX redirect response"
  [router route-name]
  (-> (ext/render-html [:div])
      (response/header "HX-Redirect" (ext/get-route router route-name))))

(defn redirect-with-session
  "Create an HTMX redirect response with session data"
  [router route-name session-data]
  (-> (redirect-response router route-name)
      (assoc :session session-data)))

(defn form-error-response
  "Render a form with validation errors"
  [view-fn data]
  (ext/render-html (view-fn data)))

; Password utilities
(defn verify-password
  "Safely verify a password, returning {:valid boolean}"
  [password user-password]
  (try
    (hashers/verify password user-password {:alg PASSWORD-HASH-ALGORITHM})
    (catch Exception _e
      {:valid false})
    (catch AssertionError _e
      {:valid false})))

; JWT utilities
(defn create-reset-token
  "Create a password reset JWT token"
  [user-id email secret-key]
  (let [now (Instant/now)
        claims {:sub user-id
                :email email
                :exp (.getEpochSecond (.plus now (Duration/ofHours 24)))
                :iat (.getEpochSecond now)}]
    (jwt/sign claims secret-key {:alg JWT-ALGORITHM})))

(defn verify-reset-token
  "Verify and decode a password reset token"
  [token secret-key]
  (try
    {:valid true
     :claims (jwt/unsign token secret-key {:alg JWT-ALGORITHM})}
    (catch Exception _e
      {:valid false})))

(defn get-register
  [{router :reitit.core/router}]
  (-> {:router router}
      (views/register-page)
      (ext/render-html)))

(defn post-register
  [{:keys [context errors parameters params]
    router :reitit.core/router}]
  (if (some? errors)
    (form-error-response views/register-form {:router router
                                              :values params
                                              :errors (:humanized errors)})
    (let [{:keys [email password]} (:form parameters)
          base-data {:router router
                     :values params}]
      (try
        (let [user (queries/create-user! (:db context) {:email email
                                                        :password password})]
          (redirect-with-session router ::routes/home {:identity (dissoc user :password)}))
        (catch SQLException e
          (let [error-msg (if (re-find #"UNIQUE constraint failed" (ex-message e))
                            "user already exists"
                            "unexpected database error while creating account")]
            (form-error-response views/register-form (assoc base-data :errors {:email [error-msg]}))))
        (catch Exception _e
          (form-error-response views/register-form
                               (assoc base-data :errors {:common ["unexpected server error"]})))))))

(defn get-login
  [{router :reitit.core/router}]
  (-> {:router router}
      (views/login-page)
      (ext/render-html)))

(defn post-login
  [{:keys [errors params parameters context]
    router :reitit.core/router}]
  (if (some? errors)
    (form-error-response views/login-form {:router router
                                           :values params
                                           :errors (:humanized errors)})
    (let [{:keys [email password]} (:form parameters)
          user (queries/get-user (:db context) email)
          {:keys [valid]} (verify-password password (:password user))]
      (if (and (some? user) valid)
        (redirect-with-session router ::routes/home {:identity (dissoc user :password)})
        (form-error-response views/login-form {:router router
                                               :values params
                                               :errors {:common ["Invalid email or password"]}})))))

(defn post-logout
  [{router :reitit.core/router}]
  (redirect-with-session router ::routes/home nil))

(defn get-account
  [request]
  (-> {:user (:identity request)
       :router (:reitit.core/router request)}
      (views/account-page)
      (ext/render-html)))

(defn post-change-password
  [{:keys [context errors parameters params]
    user :identity
    router :reitit.core/router}]
  (if (seq errors)
    (form-error-response views/change-password-form {:user user
                                                     :router router
                                                     :values params
                                                     :errors (:humanized errors)})
    (let [{:keys [current-password new-password confirm-new-password]} (:form parameters)
          user (queries/get-user (:db context) (:email user))
          {:keys [valid]} (verify-password current-password (:password user))
          base-data {:user user
                     :router router
                     :values params}]
      (cond
        (not valid)
        (form-error-response views/change-password-form
                             (assoc base-data :errors {:current-password ["Current password is incorrect"]}))

        (not= new-password confirm-new-password)
        (form-error-response views/change-password-form
                             (assoc base-data :errors {:common ["New passwords do not match"]}))

        :else
        (let [password-hash (hashers/derive new-password {:alg PASSWORD-HASH-ALGORITHM})]
          (queries/update-password! (:db context) {:id (:id user)
                                                   :password-hash password-hash})
          (form-error-response views/change-password-form
                               (assoc base-data :password-changed? true)))))))

(defn get-forgot-password
  [{router :reitit.core/router}]
  (-> {:router router}
      (views/forgot-password-page)
      (ext/render-html)))

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
    (form-error-response views/forgot-password-form {:router router
                                                     :values params
                                                     :errors (:humanized errors)})
    (let [{:keys [email]} (:form parameters)
          user (queries/get-user (:db context) email)]
      (when (some? user)
        (let [token (create-reset-token (:id user) email (:session-secret-key (:options context)))
              reset-link (str (-> request :headers (get "host"))
                              (ext/get-route router ::routes/reset-password)
                              "?token=" token)]
          (send-email! {:email email
                        :reset-link reset-link})))
      (form-error-response views/forgot-password-form {:router router
                                                       :email-sent? true}))))

(defn get-reset-password
  [{:keys [parameters context]
    router :reitit.core/router}]
  (let [token (get-in parameters [:query :token])
        {:keys [valid claims]} (verify-reset-token token (:session-secret-key (:options context)))]
    (if valid
      (ext/render-html (views/reset-password-page {:router router
                                                   :token token
                                                   :email (:email claims)}))
      (-> (ext/render-html (views/invalid-reset-token-page {:router router}))
          (response/status 400)))))

(defn post-reset-password
  [{:keys [errors params parameters context]
    router :reitit.core/router}]
  (if (seq errors)
    (form-error-response views/reset-password-form {:router router
                                                    :values (dissoc params :token)
                                                    :token (:token params)
                                                    :errors (:humanized errors)})
    (let [{:keys [password confirm-password token]} (:form parameters)
          base-data {:router router
                     :values (dissoc params :token)
                     :token token}]
      (if (not= password confirm-password)
        (form-error-response views/reset-password-form
                             (assoc base-data :errors {:common ["Passwords do not match"]}))
        (let [{:keys [valid claims]} (verify-reset-token token (:session-secret-key (:options context)))]
          (if valid
            (let [user-id (:sub claims)
                  password-hash (hashers/derive password {:alg PASSWORD-HASH-ALGORITHM})]
              (queries/update-password! (:db context) {:id user-id
                                                       :password-hash password-hash})
              (ext/render-html (views/password-reset-success-page {:router router})))
            (form-error-response views/reset-password-form
                                 (assoc base-data :errors {:common ["Invalid or expired token"]}))))))))
