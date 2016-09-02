(ns algopop.napkindo.views.root
  (:require
    [algopop.napkindo.firebase :as firebase]
    [algopop.napkindo.model :as model]
    [algopop.napkindo.names :as names]
    [algopop.napkindo.views.login :as login]
    [algopop.napkindo.views.draw :as draw]
    [algopop.napkindo.views.gallery :as gallery]
    [bidi.bidi :as bidi]
    [cljs.tools.reader.edn :as edn]
    [clojure.string :as string]
    [reagent.core :as reagent]))

(defn maybe-update [m k f]
  (if (contains? m k)
    (update m k f)
    m))

(defn draw-view [{:keys [id]}]
  (if-let [uid (:uid @firebase/user "anonymous")]
    (if (= id "new")
      (do
        ;; TODO: don't wait for result back for new drawings, work offline
        (->> (firebase/push ["users" uid "drawings"])
             (.-key)
             (str "#/draw/")
             (set! js/window.location.hash))
        [:p "Loading..."])
      ;; TODO: is this just firebase/once now?
      (let [drawing (reagent/atom nil)
            r (doto (firebase/db-ref ["users" uid "drawings" id])
                (.once
                  "value"
                  (fn [snapshot]
                    (reset! drawing
                            (merge {:svg []
                                    :title (names/sketch-name)}
                                   (-> (.val snapshot)
                                       (js->clj :keywordize-keys true)
                                       (maybe-update :svg edn/read-string)))))))]
        [draw/draw
         drawing
         (fn save-drawing []
           (.set r (-> @drawing
                       (update :svg pr-str)
                       (assoc :created firebase/timestamp)
                       (clj->js))))]))
    [:h2 "Must be logged in to draw"]))

(defn home [params]
  [:div.mdl-card.mdl-shadow--2dp
   {:style {:width "80%"}}
   [:div.mdl-card__title
    [:h2.mdl-card__title-text "Welcome"]]
   [:div.mdl-card__supporting-text
    [:ul {:style {:list-style "none"}}
     [:li "Draw your ideas as you think them."]
     [:li "Ideas are saved in the cloud."]
     [:li "Share and browse."]]]
   [:div.mdl_card__action.mdl-card--border
    [:a.mdl-button.mdl-button--colored
     {:href "#/draw/new"}
     "Get Started"]]])

(defn about [params]
  [:div "Todo: write an about"])

(defn view-drawing [{:keys [uid id]}]
  [firebase/on ["users" uid "drawings" id] draw/observe])

(def routes
  [""
   [["/" {"home" home
          "gallery" gallery/all-gallery
          "my-gallery" gallery/my-gallery
          ["view" "/" :uid "/" :id] view-drawing
          ["draw" "/" :id] draw-view
          "about" about}]
    [true gallery/all-gallery]]])

(defn navbar [handler]
  (let [anchors
        (doall
          (for [[p h params] [["Home" home]
                              ["New" draw-view [:id "new"]]
                              ["Mine" gallery/my-gallery]
                              ["Everyone's" gallery/all-gallery]]
                :let [title (string/capitalize
                              (if (sequential? p)
                                (first p)
                                p))]]
            [:a.mdl-navigation__link.mdl-button.mdl-button--raise.mdl-button--accent
             {:key title
              :href (str "#" (apply bidi/path-for routes h params))
              :style {:margin-right "15px"
                      :box-shadow (when (= h handler)
                                    "inset 0 -10px 10px -10px #FF0000")}}
             title]))]
    ;; TODO: style that works with andriod
    [:header                                                ;;.mdl-layout__header
     [:div                                                  ;;.mdl-layout__header-row
      [:div.mdl-layout-spacer]
      [:nav                                                 ;;.mdl-navigation.mdl-layout--large-screen-only
       anchors
       [:label.mdl-navigation__link.mdl-button.mdl-button-raise.mdl-button-accent "Search "
        [:input {:type "text"
                 :on-change
                 (fn [e]
                   (model/set-search! (.. e -target -value)))}]]
       [login/login-view]]]]))

(defn root [app-state]
  (let [{:keys [handler route-params]} (bidi/match-route routes (:route @app-state))]
    [:div.mdl-layout.mdl-layout--fixed-header
     [navbar handler]
     [:main
      [:section
       [handler route-params]]]]))
