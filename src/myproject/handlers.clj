(ns myproject.handlers
  (:require [myproject.views :as views]
            [malli.core :as m]
            [malli.error :as me]
            [malli.transform :as mt]
            [myproject.queries :as queries]
            [reitit-extras.core :as reitit-extras]
            [ring.util.response :as response]))

(defn validate-params!
  "Validate router parameters and return human-readable errors."
  [schema data]
  (try
    (m/coerce schema data (mt/transformer mt/strip-extra-keys-transformer
                                          mt/string-transformer))
    (catch Exception e
      {:errors (-> e (ex-data) :data :explain me/humanize)})))

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
  (let [validated-params (validate-params! [:map
                                            [:title [:string {:min 1}]]
                                            [:year pos-int?]
                                            [:director [:string {:min 1}]]]
                                           params)]
    (if (seq (:errors validated-params))
      (-> {:router router
           :errors (:errors validated-params)
           :params params}
          (views/form)
          (reitit-extras/render-html))
      (-> (list
            (views/form {:router router})
            [:template
             [:tbody
              {:hx-swap-oob "beforeend:#table-content"}
              (views/list-item {:router router
                                :movie (queries/create-movie (:db context) params)})]])
          (reitit-extras/render-html)
          (response/header "Content-Type" "text/html")))))
