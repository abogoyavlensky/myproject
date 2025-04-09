(ns myproject.handlers
  (:require [myproject.views :as views]
            [myproject.queries :as queries]
            [reitit-extras.core :as reitit-extras]
            [ring.util.response :as response]))

(defn default-handler
  [error-text status-code]
  (fn [_]
    (-> (views/error-page error-text)
        (reitit-extras/render-html)
        (response/status status-code))))

(defn home-handler
  [{:keys [context]
    router :reitit.core/router}]
  (-> {:router router
       :movies (queries/get-movie-list (:db context))}
      (views/home-page)
      (reitit-extras/render-html)))

(defn create-movie-handler
  "Render a new table item with newly created movie."
  [{router :reitit.core/router
    :keys [context params]}]
  (-> (list
        (views/form {:router router})
        [:template
         [:tbody
          {:hx-swap-oob "beforeend:#table-content"}
          (views/list-item {:router router
                            :movie (queries/create-movie (:db context) params)})]])
      (reitit-extras/render-html)
      (response/header "Content-Type" "text/html")))
