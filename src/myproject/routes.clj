(ns myproject.routes
  (:require [myproject.handlers :as handlers]
            [ring.util.response :as response]))

(def routes
  [["/" {:name ::home-page
         :get {:handler handlers/home-handler}
         :responses {200 {:body string?}}}]
   ["/health" {:name ::health-check
               :get {:handler (fn [_] (response/response "OK"))}}]
   ["/movies"
    ["" {:name ::movie-list
         :post {:handler handlers/create-movie-handler
                :responses {200 {:body string?}}}}]
    ["/:id"
     ["" {:name ::movie-details
          :delete {:handler handlers/delete-movie-handler
                   :parameters {:path {:id pos-int?}}}}]]]])
