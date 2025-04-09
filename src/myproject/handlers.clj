(ns myproject.handlers
  (:require [myproject.views :as views]
            [reitit-extras.core :as reitit-extras]
            [ring.util.response :as response]))

(defn default-handler
  [error-text status-code]
  (fn [_]
    (-> (views/error-page error-text)
        (reitit-extras/render-html)
        (response/status status-code))))

(defn home-handler
  [_]
  (-> {:movies [{:title "Movie 1"
                 :year 2023
                 :director "Director 1"}
                {:title "Movie 2"
                 :year 2022
                 :director "Director 2"}]}
      (views/home-page)
      (reitit-extras/render-html)))
