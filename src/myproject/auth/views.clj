(ns myproject.auth.views
  (:require [myproject.views :as views]
            [myproject.routes :as-alias routes]
            [reitit-extras.core :as ext]))

(defn register-page
  [{:keys [router]}]
  (views/base
    [:div {:class ["bg-white" "py-6" "sm:py-8" "lg:py-12" "mt-20"]}
     [:nav {:class ["absolute" "top-0" "left-1/4" "p-4"]}
      [:div {:class ["flex" "gap-4"]}
       (views/button {:url "/" :text "<- Home page"})]]
     [:div {:class ["mx-auto" "max-w-screen-2xl" "px-4" "md:px-8"]}
      [:h2 {:class ["mb-4" "text-center" "text-2xl" "font-bold" "text-gray-800" "md:mb-8" "lg:text-3xl"]} "Register"]
      [:form
       {:id "form-register"
        :class ["mx-auto" "max-w-lg"]
        :hx-post (ext/get-route router ::routes/register)
        :hx-target "#form-register"
        :hx-swap "outerHTML"}
       (ext/csrf-token-html)
       [:div {:class ["flex" "flex-col" "gap-4" "p-4" "md:p-8"]}
        [:div
         [:label {:class ["mb-2" "inline-block" "text-sm" "text-gray-800" "sm:text-base"] :for "email"} "Email"]
         [:input {:class ["w-full" "rounded-lg" "border" "px-3" "py-2" "text-gray-800" "outline-none" "ring-indigo-300" "transition" "duration-100" "focus:ring"]
                  :name "email"
                  :required true
                  :autocomplete "email"
                  :autocorrect "off"
                  :autocapitalize "none"
                  :type "email"}]]
        [:div
         [:label {:class ["mb-2" "inline-block" "text-sm" "text-gray-800" "sm:text-base"] :for "password"} "Password"]
         [:input {:class ["w-full" "rounded-lg" "border" "px-3" "py-2" "text-gray-800" "outline-none" "ring-indigo-300" "transition" "duration-100" "focus:ring"]
                  :type "password"
                  :name "password"
                  :required true
                  :autocomplete "new-password"
                  :autocorrect "off"
                  :autocapitalize "none"}]]
        [:button
         {:class ["block" "rounded-lg" "bg-gray-800" "px-8" "py-3" "text-center" "text-sm" "font-semibold" "text-white" "outline-none" "ring-gray-300" "transition" "duration-100" "hover:bg-gray-700" "focus-visible:ring" "active:bg-gray-600" "md:text-base"]}
         "Create an account"]]
       [:div
        [:p {:class ["text-center" "text-sm" "text-gray-500"]}
         "Already registered? "
         [:a {:class ["text-indigo-500" "transition" "duration-100" "hover:text-indigo-600" "active:text-indigo-700"] :href "/login"} "Log in"]]]]]]))

(defn login-page
  []
  (views/base
    [:div {:class ["bg-white" "py-6" "sm:py-8" "lg:py-12" "mt-20"]}
     [:nav {:class ["absolute" "top-0" "left-1/4" "p-4"]}
      [:div {:class ["flex" "gap-4"]}
       (views/button {:url "/" :text "<- Home page"})]]
     [:div {:class ["mx-auto" "max-w-screen-2xl" "px-4" "md:px-8"]}
      [:h2 {:class ["mb-4" "text-center" "text-2xl" "font-bold" "text-gray-800" "md:mb-8" "lg:text-3xl"]} "Login"]
      [:form {:class ["mx-auto" "max-w-lg"]}
       [:div {:class ["flex" "flex-col" "gap-4" "p-4" "md:p-8"]}
        [:div
         [:label {:class ["mb-2" "inline-block" "text-sm" "text-gray-800" "sm:text-base"] :for "email"} "Email"]
         [:input {:class ["w-full" "rounded-lg" "border" "px-3" "py-2" "text-gray-800" "outline-none" "ring-indigo-300" "transition" "duration-100" "focus:ring"] :name "email"}]]
        [:div
         [:label {:class ["mb-2" "inline-block" "text-sm" "text-gray-800" "sm:text-base"] :for "password"} "Password"]
         [:input {:class ["w-full" "rounded-lg" "border" "px-3" "py-2" "text-gray-800" "outline-none" "ring-indigo-300" "transition" "duration-100" "focus:ring"] :name "password"}]]
        [:div {:class ["flex" "items-end" "justify-end" "py-2"]}
         [:a {:class ["text-indigo-500" "transition" "duration-100" "hover:text-indigo-600" "active:text-indigo-700"] :href "#"} "Forgot password?"]]
        [:button {:class ["block" "rounded-lg" "bg-gray-800" "px-8" "py-3" "text-center" "text-sm" "font-semibold" "text-white" "outline-none" "ring-gray-300" "transition" "duration-100" "hover:bg-gray-700" "focus-visible:ring" "active:bg-gray-600" "md:text-base"]} "Log in"]]
       [:div {:class ["flex" "items-center" "justify-center" "p-4"]}
        [:p {:class ["text-center" "text-sm" "text-gray-500"]}
         "Don't have an account? "
         [:a {:class ["text-indigo-500" "transition" "duration-100" "hover:text-indigo-600" "active:text-indigo-700"] :href "/register"} "Register"]]]]]]))
