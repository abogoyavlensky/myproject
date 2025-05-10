(ns myproject.auth.handlers
  (:require [myproject.auth.views :as views]
            [reitit-extras.core :as reitit-extras]
            [ring.util.response :as response]))

(defn register-handler
  [_]
  (let [page (views/register-page)]
    (reitit-extras/render-html page)))
