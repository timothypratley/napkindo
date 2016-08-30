(ns algopop.napkindo.views.text-entry
  (:require
    [goog.dom.forms :as forms]
    [algopop.napkindo.firebase :as firebase]
    [algopop.napkindo.db :as db])
  (:import
    [goog.structs Map]))

(defn form-data [form]
  (into {} (for [[k v] (js->clj (.toObject (forms/getFormDataMap form)))]
             [(keyword k) (if (<= (count v) 1)
                            (first v)
                            v)])))

(defn add-entity-form [uid conn]
  [:form
   {:on-submit
    (fn edit [e]
      (.preventDefault e)
      (db/add-entity conn (form-data (.-target e)))
      (firebase/save ["users" uid "db"] (pr-str @conn)))}
   [:div#name.mdl-textfield.mdl-js-textfield
    {:style {:width "100%"}}
    [:input.mdl-textfield__input.mdl-js-textfield
     {:type "text"
      :name "name"}]
    [:label.mdl-textfield__label
     {:for "name"}
     "Title..."]]
   #_[:div#text.mdl-textfield.mdl-js-textfield.mdl-textfield--expandable
    {:style {:width "100%"}}
    [:textarea.mdl-textfield__input
     {:type "text"
      :name "text"
      :rows 5}]
    [:label.mdl-textfield__label
     {:for "text"}
     "Text..."]]
   [:center
    [:input.mdl-button.mdl-js-button--raised.mdl-button--accent
     {:type "submit"}]]])