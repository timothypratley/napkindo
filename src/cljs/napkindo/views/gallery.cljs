(ns napkindo.views.gallery
  (:require
    [reagent.core :as reagent]
    [napkindo.views.draw :as draw]
    [devcards.core]
    [napkindo.firebase :as firebase]
    [napkindo.views.d3 :as d3]
    [clojure.string :as string]
    [cljs.tools.reader.edn :as edn])
  (:require-macros
    [devcards.core :refer [defcard-rg]]))

(defn card [uid id {:keys [svg title notes created]}]
  [:span.mdl-card.mdl-shadow--2dp
   {:style {:display "inline-block"
            :width "256px"}}
   [:a.mdl-card__title.mdl-card--expand
    ;; TODO: unparse this
    {:href (str (if (= uid (:uid @firebase/user)) "#/draw/" (str "#/view/" uid "/")) id)}
    ;; TODO: put viewbox in the svg data
    ;; TODO: set as background?
    [draw/prepare-svg :svg {:width "100px" :height "100px" :view-box "0 0 400 400"} svg]]
   [:div.mdl-card__supporting-text title]
   [:div.mdl-card__actions
    {:style {:height "52px"
             :padding "16px"
             :background (d3/rgb (d3/color-for uid))}}
    (when-let [me (:uid @firebase/user)]
      (when (= me uid)
        [:span.mdl-button.mdl-button--icon
         {:on-click
          (fn [e]
            (firebase/delete ["users" me "drawings" id]))}
         [:i.material-icons "\uE872"]]))
    [:div.mdl-card__menu
     [:button.mdl-button.mdl-button--icon
      [:i.material-icons "share"]]]]])

(defn gallery [drawings]
  [:div
   (doall
     (for [[[uid id] drawing] (reverse (sort-by (comp :created val) drawings))]
       ^{:key id}
       [card uid id drawing]))])

(defcard-rg gallery-card
  [gallery
   {[1 2] {:svg [[:circle {:r 30}]]
           :title "title"
           :notes "here are some notes"}
    [3 4] {:svg [[:circle {:r 30 :cx 100 :cy 100}]]
           :title "title2 with somelongwords"
           :notes "here are some other notes"}
    [5 6] {:svg [[:circle {:r 30 :cx 150 :cy 150}]]
           :title "title3"
           :notes "some other notes"}}])

(defn all-gallery [params]
  [firebase/on ["users"]
   (fn [users]
     [gallery
      (doall
        (for [[uid user] (js->clj @users)
              :let [drawings (get user "drawings")]
              :when (map? drawings)
              [id {:strs [svg title notes created]}] drawings]
          [[uid id] {:svg (edn/read-string svg)
                     :title title
                     :notes notes
                     :created (js/Date. created)}]))])])

(defcard-rg all-gallery-card
  all-gallery)
