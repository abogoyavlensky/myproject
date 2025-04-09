(ns myproject.views
  (:require [manifest-edn.core :as manifest]
            [reitit-extras.core :as reitit-extras]
            [myproject.routes :as-alias routes]))

(defn base
  "Base component for html page."
  [content]
  [:html
   {:lang "en"}
   [:head
    [:meta {:charset "UTF-8"}]
    [:meta {:name "viewport"
            :content "width=device-width, initial-scale=1, maximum-scale=1, user-scalable=0"}]
    [:meta {:name "msapplication-TileColor"
            :content "#ffffff"}]
    [:link {:rel "manifest"
            :href "/assets/manifest.json"}]
    [:link {:rel "icon"
            :href (manifest/asset "images/icon@32px.png")}]
    [:link {:rel "icon"
            :href (manifest/asset "images/icon.svg")
            :type "image/svg+xml"}]
    [:link {:rel "apple-touch-icon"
            :sizes "180x180"
            :href (manifest/asset "images/icon@180px.png")}]
    [:link {:type "text/css"
            :href (manifest/asset "css/output.css")
            :rel "stylesheet"}]
    [:title "Clojure Stack Lite | A Template for Clojure Projects"]]
   [:body
    content
    [:script {:type "text/javascript"
              :src (manifest/asset "js/htmx.min.js")
              :defer true}]
    [:script {:type "text/javascript"
              :src (manifest/asset "js/alpinejs.min.js")
              :defer true}]]])

(defn error-page
  [text]
  (base
    [:div {:class ["mt-56"]}
     [:div {:class ["mx-auto" "text-center"]}
      [:h1 {:class ["text-5xl"]} text]]]))

(defn list-item
  [{:keys [movie]}]
  [:tr
   [:td {:class ["px-6" "py-4" "text-gray-800"]} (:title movie)]
   [:td {:class ["px-6" "py-4" "text-gray-800"]} (:year movie)]
   [:td {:class ["px-6" "py-4" "text-gray-800"]} (:director movie)]
   [:td {:class ["px-6" "py-4"]}
    [:button
     {:class ["text-red-400" "hover:bg-gray-50" "bg-white" "border"
              "border-gray-300" "rounded-md" "px-3" "py-1" "cursor-pointer"]}
     "Delete"]]])

(defn form-input
  [{:keys [field-name field-type field-value attrs]}]
  [:div
   {:class ["flex" "flex-col"]}
   [:input (merge {:class ["w-full" "border" "border-gray-300" "rounded-md" "px-3" "py-2"]
                   :type field-type
                   :name field-name
                   :value (or field-value "")
                   :placeholder (str "Enter " field-name)}
                  attrs)]])

(defn form
  [{:keys [router params]}]
  [:form
   {:id "form-create-movie"
    :class ["border-t" "border-gray-200" "bg-gray-50" "p-6"]
    :hx-post (reitit-extras/get-route router ::routes/movie-list)
    :hx-target "#form-create-movie"
    :hx-swap "outerHTML"}
   (reitit-extras/csrf-token-html)
   [:div {:class ["grid" "grid-cols-1" "md:grid-cols-4" "gap-4"]}
    (form-input {:field-name "title"
                 :field-type "text"
                 :field-value (:title params)})
    (form-input {:field-name "year"
                 :field-type "number"
                 :attrs {:min 1888}
                 :field-value (:year params)})
    (form-input {:field-name "director"
                 :field-type "text"
                 :field-value (:director params)})
    [:div {:class ["flex" "flex-col"]}
     [:button {:class ["bg-blue-600" "text-white" "rounded-md" "px-4" "py-2"
                       "hover:bg-blue-700" "cursor-pointer"]}
      "Create"]]]])

(defn home-page
  [{:keys [movies router]}]
  (base
    [:div {:id "content"
           :class ["container" "mx-auto" "p-6" "max-w-4xl"]}
     [:div {:class ["mb-10" "flex" "justify-between" "items-center"]}
      [:h1 {:class ["text-2xl" "font-semibold" "text-gray-800"]} "Movies Lite"]]
     [:div {:class ["bg-white" "rounded-lg" "shadow-md" "overflow-hidden" "border"
                    "border-gray-200"]}
      [:div {:class ["overflow-x-auto"]}
       [:table {:class ["min-w-full" "divide-y" "divide-gray-200"]}
        [:thead {:class ["bg-white"]}
         [:tr
          [:th {:class ["px-6" "py-3" "text-left" "text-gray-500" "font-medium"]
                :scope "col"} "Title"]
          [:th {:class ["px-6" "py-3" "text-left" "text-gray-500" "font-medium"]
                :scope "col"} "Year"]
          [:th {:class ["px-6" "py-3" "text-left" "text-gray-500" "font-medium"]
                :scope "col"} "Director"]
          [:th {:class ["px-6" "py-3" "text-left" "text-gray-500" "font-medium"]
                :scope "col"} "Actions"]]]
        [:tbody {:id "table-content"
                 :class ["bg-white" "divide-y" "divide-gray-200"]}
         (for [movie movies]
           (list-item {:movie movie}))]]]
      (form {:router router})]]))
