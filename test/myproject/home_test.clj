(ns myproject.home-test
  (:require [clj-http.client :as http]
            [clojure.test :refer :all]
            [hickory.core :as hickory]
            [hickory.select :as select]
            [integrant-extras.tests :as ig-extras]
            [reitit-extras.tests :as reitit-extras]
            [myproject.server :as-alias server]
            [myproject.test-utils :as test-utils]))

(use-fixtures :once
  (ig-extras/with-system))

(use-fixtures :each
  test-utils/with-truncated-tables)

(deftest test-home-page-is-loaded-correctly
  (let [server (::server/server ig-extras/*test-system*)
        url (reitit-extras/get-server-url server :host)
        body (-> (http/get url)
                 :body
                 (hickory/parse)
                 (hickory/as-hickory))]
    (is (= "Clojure Stack Lite"
           (->> body
                (select/select (select/tag :span))
                (first)
                :content
                (first))))))
