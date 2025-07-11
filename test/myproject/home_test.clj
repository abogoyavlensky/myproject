(ns myproject.home-test
  (:require [clj-http.client :as http]
            [clojure.test :refer :all]
            [hickory.select :as select]
            [integrant-extras.tests :as ig-extras]
            [myproject.server :as-alias server]
            [myproject.test-utils :as utils]
            [reitit-extras.tests :as reitit-extras]))

(use-fixtures :once
  (ig-extras/with-system))

(use-fixtures :each
  utils/with-truncated-tables)

(deftest test-home-page-is-loaded-correctly
  (let [url (reitit-extras/get-server-url (utils/server))
        response (http/get url)]
    (is (= "Clojure Stack Lite"
           (->> (utils/response->hickory response)
                (select/select (select/tag :span))
                (first)
                :content
                (first))))))
