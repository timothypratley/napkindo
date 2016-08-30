(ns algopop.napkindo.views.draw
  (:require
    [reagent.core :as reagent]
    [devcards.core]
    [clojure.string :as string]
    [cljs.tools.reader.edn :as edn])
  (:require-macros
    [devcards.core :refer [defcard-rg]]))

(def default-dims [400 400])

(defn xy [e [width height]]
  (let [rect (.getBoundingClientRect (if (exists? (.-currentTarget e))
                                       (.-currentTarget e)
                                       (.-target e)))
        ;; Android old versions don't return the correct rect width/height
        w (.. e -target -width -baseVal -value)
        h (.. e -target -height -baseVal -value)]
    [(-> (- (.-clientX e) (.-left rect))
         (/ w)
         (* width)
         (js/Math.round))
     (-> (- (.-clientY e) (.-top rect))
         (/ h)
         (* height)
         (js/Math.round))]))

(defn prepare [path mode]
  (if (= mode ::edit)
    (into
      [:g
       (update-in path [1 :d] #(string/join " " %))]
      (for [[x y] (partition 2 (filter number? (get-in path [1 :d])))]
        [:circle {:cx x :cy y :r 5 :stroke "blue" :stroke-width 1}]))
    (update-in path [1 :d] #(string/join " " %))))

(defn one-touch-handler [f]
  (fn a-one-touch-handler [e]
    (.preventDefault e)
    (let [touch (aget e "targetTouches" 0)]
      (f touch))))

(defn new-state [{:keys [index values] :as history} value]
  (if (<= (dec (count values)) index)
    (-> history
        (update :index inc)
        (update :values conj value))
    (-> history
        (update :values assoc index value)
        (update :values subvec 0 (inc index)))))

(defn undo [{:keys [index values] :as history}]
  (cond-> history
    (pos? index) (update :index dec)))

(defn redo [{:keys [index values] :as history}]
  (cond-> history
    (< index (dec (count values))) (update :index inc)))

(defn current [{:keys [index values] :as history}]
  (values index))

(defn a-draw [drawing history img mode select drag dropped start-path continue-path end-path save]
  (when @drawing
    [:div
     [:div
      ;; http://alistapart.com/article/creating-intrinsic-ratios-for-video
      {:style {:height 0
               :padding-bottom "100%"
               :position "relative"}}
      [:svg
       (merge-with
         merge
         {:view-box (string/join " " (concat [0 0] (:dims @drawing default-dims)))
          :style {:position "absolute"
                  :top 0
                  :left 0
                  :width "100%"
                  :height "100%"
                  :border "1px solid black"
                  :-webkit-touch-callout "none"
                  :-webkit-user-select "none"
                  :-khtml-user-select "none"
                  :-moz-user-select "none"
                  :-ms-user-select "none"
                  :user-select "none"}}
         (if (= @mode ::edit)
           {:style {:cursor "move"}
            :on-touch-start (one-touch-handler select)
            :on-mouse-down select
            :on-mouse-over select
            :on-touch-move (one-touch-handler drag)
            :on-mouse-move drag
            :on-touch-end (one-touch-handler dropped)
            :on-mouse-up dropped
            :on-touch-cancel (one-touch-handler dropped)
            :on-mouse-out dropped}
           {:style {:cursor "crosshair"}
            :on-touch-start (one-touch-handler start-path)
            :on-mouse-down start-path
            :on-mouse-over start-path
            :on-touch-move (one-touch-handler continue-path)
            :on-mouse-move continue-path
            :on-touch-end (one-touch-handler end-path)
            :on-mouse-up end-path
            :on-touch-cancel (one-touch-handler end-path)
            :on-mouse-out end-path}))
       (when @img
         [:image {:xlink-href @img
                  :width "100%"
                  :height "100%"
                  :opacity 0.3}])
       (into
         [:g {:fill "none"
              :style {:pointer-events "none"}
              :on-touch-start
              (fn [e] (.preventDefault e))
              :stroke "black"
              :stroke-width 5
              :stroke-linecap "round"
              :stroke-linejoin "round"}]
         (for [elem (:svg @drawing)]
           (prepare elem @mode)))]]
     [:input {:type "text"
              :style {:width "100%"}
              :default-value (:title @drawing)
              :on-blur save
              :on-change
              (fn title-changed [e]
                (swap! drawing assoc :title (.. e -target -value)))}]
     [:div
      (if (= @mode ::edit)
        [:span.mdl-button.mdl-button--icon
         {:title "Draw"
          :on-click
          (fn draw-mode [e]
            (reset! mode ::draw))}
         [:i.material-icons "edit"]]
        [:span.mdl-button.mdl-button--icon
         {:title "Edit"
          :on-click
          (fn edit-mode [e]
            (reset! mode ::edit))}
         [:i.material-icons "adjust"]])
      (if @img
        [:span.mdl-button.mdl-button--icon.active
         {:title "Clear Background"
          :on-click
          (fn image-off [e]
            (reset! img nil))}
         [:i.material-icons "image"]]
        [:label
         [:input
          {:type "file"
           :accept "image/*"
           :style {:display "none"}
           :on-change
           (fn image-selected [e]
             (let [r (js/FileReader.)]
               (set! (.-onload r)
                     (fn [e]
                       (reset! img (.. e -target -result))))
               (.readAsDataURL r (aget (.. e -target -files) 0))))}]
         [:span.mdl-button.mdl-button--icon
          {:title "Background"}
          [:i.material-icons "image"]]])
      [:span.mdl-button.mdl-button--icon
       {:title "Undo"
        :on-click
        (fn undo-click [e]
          (reset! drawing (current (swap! history undo))))}
       [:i.material-icons "undo"]]
      [:span.mdl-button.mdl-button--icon
       {:title "Redo"
        :on-click
        (fn redo-click [e]
          (reset! drawing (current (swap! history redo))))}
       [:i.material-icons "redo"]]
      [:span.mdl-button.mdl-button--icon
       {:title "New"
        :on-click
        (fn draw-new [e]
          (set! js/window.location.hash "#/draw/new"))}
       [:i.material-icons "\uE31B"]]]
     [:textarea
      {:rows 5
       :style {:width "100%"}
       :default-value (:notes @drawing)
       :on-blur save
       :on-change
       (fn notes-entered [e]
         (swap! drawing assoc :notes (.. e -target -value)))}]]))

(defn draw [drawing save]
  ;; TODO: intial state should be set, but can't deref drawing hmmmm.
  ;; also should probably save after undo/redo?
  (let [history (atom {:index -1
                       :values []})
        save (fn []
               (swap! history new-state @drawing)
               (save))
        img (reagent/atom nil)
        pen-down? (reagent/atom false)
        mode (reagent/atom ::draw)
        selected (reagent/atom nil)
        start-path
        (fn start-path [e]
          (when (not= (.-buttons e) 0)
            (reset! pen-down? true)
            (let [[x y] (xy e (:dims @drawing default-dims))]
              (swap! drawing update :svg conj [:path {:d ['M x y 'L x y]}]))))
        continue-path
        (fn continue-path [e]
          (when @pen-down?
            (let [[x y] (xy e (:dims @drawing default-dims))]
              (swap! drawing update :svg #(update-in % [(dec (count %)) 1 :d] conj x y)))))
        end-path
        (fn end-path [e]
          (when @pen-down?
            (when e
              (continue-path e))
            (reset! pen-down? false)
            (when save
              (save))))
        select
        (fn [e]
          (reset! selected (.-target e)))
        drag
        (fn [e]
          (prn "drag"))
        dropped
        (fn [e]
          (prn "dropped"))]
    [a-draw drawing history img mode select drag dropped start-path continue-path end-path save]))

(defcard-rg draw-card
  [draw (reagent/atom {:svg []}) nil])

(defn prepare-svg [tag properties elems]
  (into
    [tag (merge {:fill "none"
                 :stroke "black"
                 :stroke-width 5
                 :stroke-linecap "round"
                 :stroke-linejoin "round"}
                properties)]
    (for [elem elems]
      (prepare elem ::draw))))

(defn observe [drawing]
  [prepare-svg :svg
   {:view-box (string/join " " (concat [0 0] (:dims @drawing default-dims)))
    :style {:border "1px solid black"
            ;; TODO: moar browzazs
            :-webkit-user-select "none"}}
   (edn/read-string (some-> @drawing (.-svg)))])

(defcard-rg view-card
  [observe
   (reagent/atom
     #js {"svg" "[[:path {:d [M 50 50 L 100 100]}] [:path {:d [M 200 200 L 300 300]}]]"})])
