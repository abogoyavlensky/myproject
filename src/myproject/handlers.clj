(ns myproject.handlers
  (:require [myproject.views :as views]
            [reitit-extras.core :as ext]
            [ring.util.response :as response]))

(defn default-handler
  [error-text status-code]
  (fn [_]
    (-> (views/error-page error-text)
        (ext/render-html)
        (response/status status-code))))

(defn home-handler
  [{router :reitit.core/router
    :as request}]
  (-> {:user (:identity request)
       :router router}
      (views/home-page)
      (ext/render-html)))
