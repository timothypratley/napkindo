(ns algopop.napkindo.views.gallery
  (:require
    [algopop.napkindo.firebase :as firebase]
    [algopop.napkindo.model :as model]
    [algopop.napkindo.views.draw :as draw]
    [cljs.tools.reader.edn :as edn]
    [clojure.string :as string]
    [devcards.core]
    [goog.crypt :as crypt])
  (:require-macros
    [devcards.core :refer [defcard-rg]])
  (:import
    (goog.crypt Md5)))

(defn color-for [uid]
  (let [h (hash uid)]
    [(bit-and 0xff h)
     (bit-and 0xff (bit-shift-right h 8))
     (bit-and 0xff (bit-shift-right h 16))]))

(defn rgb [[r g b]]
  (str "rgb(" r "," g "," b ")"))

(defn md5-hash [s]
  (let [md5 (Md5.)]
    (.update md5 (string/trim s))
    (crypt/byteArrayToHex (.digest md5))))

(defn card [uid id {:keys [svg title notes created owner]}]
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
             :background (rgb (color-for uid))}}
    (when-let [me (:uid @firebase/user)]
      (if (= me uid)
        [:span.mdl-button.mdl-button--icon
         {:on-click
          (fn [e]
            (firebase/delete ["users" me "drawings" id]))}
         [:i.material-icons "\uE872"]]
        (let [photo (or (get-in owner ["settings" "photo-url"])
                        (str "//www.gravatar.com/avatar/" (md5-hash uid) "?d=wavatar"))]
          [:span.mdl-button.mdl-button--fab.mdl-button--mini-fab
           {:title (get-in owner "[settings  display-name" "")
            :style {:cursor "default"
                    :background-image (str "url(" photo ")")
                    :background-size "cover"
                    :background-repeat "no-repeat"}}])))
    [:div.mdl-card__menu
     [:a.mdl-button.mdl-button--icon
      {:href (str (if (= uid (:uid @firebase/user)) "#/draw/" (str "#/view/" uid "/")) id)}
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

(defn my-gallery [params]
  (if-let [uid (:uid @firebase/user)]
    [firebase/on ["users" uid "drawings"]
     (fn [drawings]
       (let [drawings (js->clj @drawings)
             search (:search @model/app-state)]
         [gallery
          (doall
            (for [[id {:strs [svg title notes created]}] drawings
                  :when (or (string/blank? search) (re-find (re-pattern (str "(?i)" search))
                                                            (str title notes created)))]
              [[uid id] {:svg (edn/read-string svg)
                         :title title
                         :notes notes
                         :created (js/Date. created)}]))]))]
    [:h4 "Sign in (top right corner) to see your gallery."]))

(defn all-gallery [params]
  [firebase/on ["users"]
   (fn [users]
     [gallery
      (let [search (:search @model/app-state)]
        (doall
          (for [[uid user] (js->clj @users)
                :let [drawings (get user "drawings")]
                :when (map? drawings)
                [id {:strs [svg title notes created]}] drawings
                :when (or (string/blank? search) (re-find (re-pattern (str "(?i)" search))
                                                          (str (get-in user ["settings" "display-name"]) title notes created)))]
            [[uid id] {:svg (edn/read-string svg)
                       :owner user
                       :title title
                       :notes notes
                       :created (js/Date. created)}])))])])

(defcard-rg all-gallery-card
  all-gallery)
