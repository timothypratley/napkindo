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
      (let [drawing (reagent/atom {:svg []
                                   :title (names/sketch-name)})
            r (doto (firebase/db-ref ["users" uid "drawings" id])
                (.once
                  "value"
                  (fn success [snapshot]
                    ;; TODO: make this a non-destructive merge
                    (swap! drawing merge
                           (-> (.val snapshot)
                               (js->clj :keywordize-keys true)
                               (maybe-update :svg edn/read-string))))
                  (fn failure [error]
                    (js/alert "Connection failure:" error))))]
        [draw/draw
         drawing
         (fn save-drawing []
           (.set r (-> @drawing
                       (update :svg pr-str)
                       (assoc :created firebase/timestamp)
                       (clj->js))))]))
    [:h2 "Must be logged in to draw"]))

(defn home [params]
  [:div
   [:div.mdl-card.mdl-shadow--2dp
    {:style {:width "100%"}}
    [:div.mdl-card__title
     [:h2.mdl-card__title-text "Welcome"]]
    [:div.mdl-card__supporting-text
     [:ul {:style {:list-style "none"}}
      [:li "Draw your ideas as you think them."]
      [:li "Ideas are saved in the cloud."]
      [:li "Share and browse."]]
     [:em "\"Everyone is born creative. Everyone is given a box of crayons in kindergarten.\""]
     " -- Hugh MacLeod"]
    [:div.mdl_card__action.mdl-card--border
     [:a.mdl-button.mdl-button--colored
      {:href "#/draw/new"
       :style {:box-shadow "inset -1px -1px 0 #3f51b5"
               :border-radius "20px"}}
      "Get Started"]]]
   [:center
    "Please send me feedback: "
    [:a {:href "mailto:timothypratley@gmail.com"}
     "timothypratley@gmail.com"]]])

(defn view-drawing [{:keys [uid id]}]
  [firebase/on ["users" uid "drawings" id] draw/observe])

(def routes
  [""
   [["/" {"home" home
          "gallery" gallery/all-gallery
          "my-gallery" gallery/my-gallery
          ["view" "/" :uid "/" :id] view-drawing
          ["draw" "/" :id] draw-view}]
    [true home]]])

(defn navbar [handler]
  (let [anchors
        (doall
          (for [[p h params] [["Home" home]
                              ["Everyone's" gallery/all-gallery]
                              ["Mine" gallery/my-gallery]]
                :let [title (string/capitalize
                              (if (sequential? p)
                                (first p)
                                p))]]
            [:a.mdl-navigation__link.mdl-button.mdl-button--accent
             {:key title
              :href (str "#" (apply bidi/path-for routes h params))
              :style {:box-shadow (when (= h handler)
                                    "inset 0 -3px 0 #ff4081")}}
             title]))]
    ;; TODO: style that works with andriod
    [:header                                                ;;.mdl-layout__header
     [:div                                                  ;;.mdl-layout__header-row
      [:div.mdl-layout-spacer]
      [:nav                                                 ;;.mdl-navigation.mdl-layout--large-screen-only
       {:style {:border-bottom "1px solid lightgrey"
                :margin-bottom "20px"}}
       anchors
       [:label.mdl-navigation__link.mdl-button.mdl-button-accent "Search "
        [:input {:type "text"
                 :on-change
                 (fn [e]
                   (model/set-search! (.. e -target -value)))}]]
       [:a.mdl-navigation__link.mdl-button.mdl-button--colored
        {:href (str "#" (bidi/path-for routes draw-view :id "new"))
         :style {:border-radius "20px"
                 :box-shadow "inset -1px -1px 0 #3f51b5"}}
        [:i.material-icons "\uE254"]
        " New"]
       [login/login-view]]]]))

(defn root [app-state]
  (let [{:keys [handler route-params]} (bidi/match-route routes (:route @app-state))]
    [:div
     [:div.mdl-layout.mdl-layout--fixed-header
      [navbar handler]
      [:main
       [:section
        [handler route-params]]]]]))
