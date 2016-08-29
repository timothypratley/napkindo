(ns napkindo.views.login
  (:require
    [napkindo.firebase :as firebase]
    [devcards.core])
  (:require-macros
    [devcards.core :refer [defcard-rg]]))

(defn login-view []
  [:div
   {:style {:float "right"}}
   (if-let [{:keys [photoURL displayName]} @firebase/user]
     [:span
      [:button.mdl-button.mdl-js-button.mdl-button--fab.mdl-button--colored
       {:on-click
        (fn logout-click [e]
          (firebase/sign-out))
        :title displayName
        :style {:background-image (str "url(" photoURL ")")
                :background-size "cover"
                :background-repeat "no-repeat"}}]]
     ;; The firebase login doesn't work on mobile devices :(
     #_[:button.mdl-button.mdl-button--raised.mdl-button--colored
      {:on-click
       (fn login-click [e]
         (firebase/sign-in))}
      "Login with Google"])])

(defcard-rg login-card
  [:div
   [login-view]
   [:div "Click the button to login"]
   [:div "Click your picture to log out"]])
