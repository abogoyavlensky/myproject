(ns myproject.auth.views
  (:require [myproject.routes :as-alias routes]
            [myproject.views :as views]
            [reitit-extras.core :as ext]))

(defn- form-input
  [{:keys [input-name input-type input-value errors props]}]
  [:div
   [:label {:class ["mb-2" "inline-block" "text-sm" "text-gray-800" "sm:text-base"]
            :for "email"} "Email"]
   [:input (merge {:class ["w-full" "rounded-lg" "border" "px-3" "py-2" "text-gray-800"
                           "outline-none" "ring-indigo-300" "transition" "duration-100" "focus:ring"
                           (when (seq errors) "border-red 500")]
                   :name input-name
                   :type input-type
                   :value input-value
                   :autocorrect "off"
                   :autocapitalize "none"}
                  props)]
   (for [error-message errors]
     [:p {:class ["text-red-500" "text-sm" "mt-1"]} error-message])])

(defn register-form
  [{:keys [router errors values]}]
  [:form
   {:id "form-register"
    :class ["mx-auto" "max-w-lg"]
    :hx-post (ext/get-route router ::routes/register)
    :hx-target "#form-register"
    :hx-swap "outerHTML"}
   (ext/csrf-token-html)
   [:div {:class ["flex" "flex-col" "gap-4" "p-4" "md:p-8"]}
    (form-input {:input-name "email"
                 :input-type "email"
                 :input-value (:email #p values)
                 :errors (:email errors)
                 ;:required true
                 :props {:autocomplete "email"}})
    (form-input {:input-name "password"
                 :input-type "password"
                 :input-value (:password #p values)
                 :errors (:password errors)
                 ;:required true
                 :props {:autocomplete "new-password"}})
    [:button
     {:class ["block" "rounded-lg" "bg-gray-800" "px-8" "py-3" "text-center" "text-sm"
              "font-semibold" "text-white" "outline-none" "ring-gray-300" "transition"
              "duration-100" "hover:bg-gray-700" "focus-visible:ring" "active:bg-gray-600"
              "md:text-base" "cursor-pointer"]
      :type "submit"}
     "Create an account"]]
   [:div
    [:p {:class ["text-center" "text-sm" "text-gray-500"]}
     "Already registered? "
     [:a {:class ["text-indigo-500" "transition" "duration-100" "hover:text-indigo-600" "active:text-indigo-700"]
          :href "/login"} "Log in"]]]])

(defn register-page
  [args]
  (views/base
    [:div {:class ["bg-white" "py-6" "sm:py-8" "lg:py-12" "mt-20"]}
     [:nav {:class ["absolute" "top-0" "left-1/4" "p-4"]}
      [:div {:class ["flex" "gap-4"]}
       (views/button {:url "/"
                      :text "<- Home page"})]]
     [:div {:class ["mx-auto" "max-w-screen-2xl" "px-4" "md:px-8"]}
      [:h2 {:class ["mb-4" "text-center" "text-2xl" "font-bold" "text-gray-800" "md:mb-8" "lg:text-3xl"]} "Register"]
      (register-form args)]]))

(defn login-page
  []
  (views/base
    [:div {:class ["bg-white" "py-6" "sm:py-8" "lg:py-12" "mt-20"]}
     [:nav {:class ["absolute" "top-0" "left-1/4" "p-4"]}
      [:div {:class ["flex" "gap-4"]}
       (views/button {:url "/"
                      :text "<- Home page"})]]
     [:div {:class ["mx-auto" "max-w-screen-2xl" "px-4" "md:px-8"]}
      [:h2 {:class ["mb-4" "text-center" "text-2xl" "font-bold" "text-gray-800" "md:mb-8" "lg:text-3xl"]} "Login"]
      [:form {:class ["mx-auto" "max-w-lg"]}
       [:div {:class ["flex" "flex-col" "gap-4" "p-4" "md:p-8"]}
        [:div
         [:label {:class ["mb-2" "inline-block" "text-sm" "text-gray-800" "sm:text-base"]
                  :for "email"} "Email"]
         [:input {:class ["w-full" "rounded-lg" "border" "px-3" "py-2" "text-gray-800" "outline-none" "ring-indigo-300" "transition" "duration-100" "focus:ring"]
                  :name "email"}]]
        [:div
         [:label {:class ["mb-2" "inline-block" "text-sm" "text-gray-800" "sm:text-base"]
                  :for "password"} "Password"]
         [:input {:class ["w-full" "rounded-lg" "border" "px-3" "py-2" "text-gray-800" "outline-none" "ring-indigo-300" "transition" "duration-100" "focus:ring"]
                  :name "password"}]]
        [:div {:class ["flex" "items-end" "justify-end" "py-2"]}
         [:a {:class ["text-indigo-500" "transition" "duration-100" "hover:text-indigo-600" "active:text-indigo-700"]
              :href "#"} "Forgot password?"]]
        [:button {:class ["block" "rounded-lg" "bg-gray-800" "px-8" "py-3" "text-center" "text-sm" "font-semibold" "text-white" "outline-none" "ring-gray-300" "transition" "duration-100" "hover:bg-gray-700" "focus-visible:ring" "active:bg-gray-600" "md:text-base" "cursor-pointer"]} "Log in"]]
       [:div {:class ["flex" "items-center" "justify-center" "p-4"]}
        [:p {:class ["text-center" "text-sm" "text-gray-500"]}
         "Don't have an account? "
         [:a {:class ["text-indigo-500" "transition" "duration-100" "hover:text-indigo-600" "active:text-indigo-700"]
              :href "/register"} "Register"]]]]]]))
