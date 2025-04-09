(ns myproject.routes
  (:require [ring.util.response :as response]
            [myproject.handlers :as handlers]))

(def routes
  [["/" {:name ::home-page
         :get {:handler handlers/home-handler}
         :responses {200 {:body string?}}}]
   ["/health" {:name ::health-check
               :get {:handler (fn [_] (response/response "OK"))}}]])
