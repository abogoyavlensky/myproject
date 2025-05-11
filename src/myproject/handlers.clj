(ns myproject.handlers
  (:require [malli.core :as m]
            [malli.error :as me]
            [malli.transform :as mt]
            [myproject.views :as views]
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
  [_]
  (reitit-extras/render-html (views/home-page)))
