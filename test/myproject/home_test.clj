(ns myproject.home-test
  (:require [clj-http.client :as http]
            [myproject.db :as db]
            [myproject.queries :as queries]
            [clojure.test :refer :all]
            [hickory.core :as hickory]
            [hickory.select :as select]
            [integrant-extras.tests :as ig-extras]
            [myproject.server :as-alias server]
            [myproject.test-utils :as test-utils]
            [reitit-extras.tests :as reitit-extras]))

(use-fixtures :once
  (ig-extras/with-system))

(use-fixtures :each
  test-utils/with-truncated-tables)

(deftest test-home-page-is-loaded-correctly
  (let [server (::server/server ig-extras/*test-system*)
        db (::db/db ig-extras/*test-system*)
        url (reitit-extras/get-server-url server :host)
        _ (queries/create-movie db {:title "The Matrix"
                                    :year 1999
                                    :director "Lana Wachowski, Lilly Wachowski"})
        body (-> (http/get url)
                 :body
                 (hickory/parse)
                 (hickory/as-hickory))]
    (is (= "Movies Lite"
           (->> body
                (select/select (select/tag :h1))
                (first)
                :content
                (first))))
    (is (= ["The Matrix" "1999" "Lana Wachowski, Lilly Wachowski"]
          (->> body
               (select/select (select/tag :td))
               (map (comp first :content))
               (butlast))))))

(deftest test-create-movie-ok
  (let [server (::server/server ig-extras/*test-system*)
        db (::db/db ig-extras/*test-system*)
        base-url (reitit-extras/get-server-url server :host)
        url (str base-url "/movies")
        {:keys [csrf-token cookies]} (test-utils/get-csrf-token-and-cookies base-url)]
    ; Create a new movie
    (http/post url {:cookies cookies
                    :form-params {test-utils/CSRF-TOKEN-KEY csrf-token
                                  :title "The Matrix"
                                  :year 1999
                                  :director "Lana Wachowski, Lilly Wachowski"}})

    (is (= [{:director "Lana Wachowski, Lilly Wachowski"
             :title "The Matrix"
             :year 1999}]
           (db/exec! db {:select [:title :year :director]
                         :from [:movie]})))))

(deftest test-delete-movie-ok
  (let [server (::server/server ig-extras/*test-system*)
        db (::db/db ig-extras/*test-system*)
        base-url (reitit-extras/get-server-url server :host)
        movie (queries/create-movie db {:title "The Matrix"
                                        :year 1999
                                        :director "Lana Wachowski, Lilly Wachowski"})
        url (str base-url "/movies/" (:id movie))
        {:keys [csrf-token cookies]} (test-utils/get-csrf-token-and-cookies base-url)]
    ; Delete the movie
    (http/delete url {:cookies cookies
                      :headers {test-utils/CSRF-TOKEN-HEADER csrf-token}})

    (is (= [] (db/exec! db {:select [:*]
                            :from [:movie]})))))
