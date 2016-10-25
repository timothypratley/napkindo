(ns algopop.napkindo.views.draw
  (:require
    [algopop.napkindo.model :as model]
    [algopop.napkindo.names :as names]
    [clojure.string :as string]
    [devcards.core]
    [reagent.core :as reagent]
    [reagent.ratom :as ratom])
  (:require-macros
    [devcards.core :refer [defcard-rg]]))

(def default-dims [400 400])

(defn xy [e [width height] elem]
  (let [rect (.getBoundingClientRect elem)]
    [(-> (- (.-clientX e) (.-left rect))
         (/ (.-width rect))
         (* width)
         (js/Math.round))
     (-> (- (.-clientY e) (.-top rect))
         (/ (.-height rect))
         (* height)
         (js/Math.round))]))

(defn vec-remove
  "remove elem in coll"
  [v pos]
  (if (< pos (count v))
    (vec (concat (subvec v 0 pos) (subvec v (inc pos))))
    v))

(defn erase [drawing idx]
  (update drawing :svg vec-remove idx))



;; TODO: clean this up
(defn remove-point [drawing element-idx path-idx]
  (update-in
    drawing
    [:svg element-idx 1 :d]
    (fn [path]
      (when-let [c (loop [cnt 0
                          idx 0
                          [x & remaining] path]
                     (cond
                       (nil? x) nil
                       (and (number? x) (= path-idx idx)) cnt
                       (number? x) (recur (+ cnt 2) (inc idx) (rest remaining))
                       :else (recur (inc cnt) idx remaining)))]
        (if (<= c 1)
          (into
            ['M (nth path 4) (nth path 5) 'L]
            (drop 6 path))
          (-> path
              (vec-remove c)
              (vec-remove c)))))))

(defn erase-point [drawing element-idx path-idx]
  (if (<= (count (get-in drawing [:svg element-idx 1 :d]))
          6)
    (erase drawing element-idx)
    (remove-point drawing element-idx path-idx)))

(defn prepare [[tag attrs] mode element-idx drawing save]
  (if (= mode ::draw)
    [tag (update attrs :d #(string/join " " %))]
    (into
      [:g
       [tag (update attrs :d #(string/join " " %))]]
      (map-indexed
        (fn [path-idx [x y]]
          [:circle
           {:on-mouse-down
            (if (= mode ::erase)
              (fn [e]
                (swap! drawing erase-point element-idx path-idx)
                (when save (save)))
              (fn [e]
                ;; TODO: grab it?
                ))
            :cx x
            :cy y
            :r (if (= mode ::erase) 3 5)
            :fill (if (= mode ::erase) "white" "none")
            :stroke (if (= mode ::erase) "red" "blue")
            :stroke-width 1}])
        (partition 2 (filter number? (:d attrs)))))))

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

(defn toolbar [drawing history mode img save]
  [:div
   [:button.mdl-button.mdl-button--icon
    {:title "Draw"
     :class (when (= @mode ::draw) "mdl-button--colored")
     :on-click
     (fn draw-mode [e]
       (reset! mode ::draw))}
    [:i.material-icons "\uE3C9"]]
   [:button.mdl-button.mdl-button--icon
    {:title "Edit"
     :class (when (= @mode ::edit) "mdl-button--colored")
     :on-click
     (fn edit-mode [e]
       (reset! mode ::edit))}
    [:i.material-icons "\uE39E"]]
   [:button.mdl-button.mdl-button--icon
    {:title "Erase"
     :class (when (= @mode ::erase) "mdl-button--colored")
     :on-click
     (fn erase-mode [e]
       (reset! mode ::erase))}
    [:i.material-icons "\uE14C"]]
   (if @img
     [:button.mdl-button.mdl-button--icon
      {:title "Clear Background"
       :on-click
       (fn image-off [e]
         (reset! img nil))}
      [:i.material-icons "\uE3F4"]]
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
       [:i.material-icons "\uE3F4"]]])
   [:label.mdl-button
    {:title "Pen"
     :for "pen_select"}
    [:i.material-icons "\uE91A"]
    [:select
     {:id "pen_select"
      :value (get-in @drawing [:svg-attrs :stroke-width] 3)
      :on-change
      (fn pen-selected [e]
        (let [stroke-width (js/parseInt (.. e -target -value))]
          (swap! drawing assoc-in [:svg-attrs :stroke-width] stroke-width)
          (when save (save))))}
     [:option {:value 1} 1]
     [:option {:value 2} 2]
     [:option {:value 3} 3]
     [:option {:value 4} 4]
     [:option {:value 5} 5]]]
   [:button#color_select.mdl-button.mdl-js-button.mdl-button--icon
    {:title "Color"}
    [:i.material-icons "\uE3B7"]]
   (into
     [:ul.mdl-menu.mdl-menu--bottom-left.mdl-js-menu.mdl-js-ripple-effect
      {:for "color_select"
       :value (get-in @drawing [:svg-attrs :stroke] "black")}]
     (for [color model/colors]
       [:li.mdl-menu__item
        {:on-click
         (fn color-selected [e]
           (swap! drawing assoc-in [:svg-attrs :stroke] color)
           (when save (save)))
         :style {:background-color color}}
        color]))
   [:button.mdl-button.mdl-button--icon
    {:title "Undo"
     :on-click
     (fn undo-click [e]
       (reset! drawing (current (swap! history undo))))}
    [:i.material-icons "\uE166"]]
   [:button.mdl-button.mdl-button--icon
    {:title "Redo"
     :on-click
     (fn redo-click [e]
       (reset! drawing (current (swap! history redo))))}
    [:i.material-icons "\uE15A"]]
   [:button.mdl-button.mdl-button--icon
    {:title "New"
     :on-click
     (fn draw-new [e]
       (set! js/window.location.hash "#/draw/new"))}
    [:i.material-icons "\uE31B"]]])

(defn paths [drawing mode save]
  (into
    [:g
     (merge
       {:style {:pointer-events (if (= ::erase @mode)
                                  "visiblePainted"
                                  "none")}
        :on-touch-start
        (fn [e]
          (.preventDefault e))
        :fill "none"
        :stroke "black"
        :stroke-width 4
        :stroke-linecap "round"
        :stroke-linejoin "round"}
       (:svg-attrs @drawing))]
    (map-indexed
      (fn [idx elem]
        (prepare
          (cond-> elem
            (= ::erase @mode) (assoc-in [1 :on-mouse-down]
                                        (fn [e]
                                          (swap! drawing erase idx)
                                          (when save (save)))))
          @mode
          idx
          drawing
          save))
      (:svg @drawing))))

(defn a-draw [drawing dims notes history img mode container
              select drag dropped start-path continue-path end-path save]
  [:div
   [:div.mdl-grid
    [:div.mdl-cell.mdl-cell--6-col
     [toolbar drawing history mode img save]]
    [:div.mdl-cell.mdl-cell--6-col
     [:button.mdl-button.mdl-button--icon
      {:style {:float "right"}
       :on-click
       (fn randomize-title [e]
         (swap! drawing assoc :title (names/sketch-name)))}
      [:i.material-icons "\uEB40"]]
     [:span
      {:style {:display "block"
               :overflow "hidden"}}
      [:input.mdl-textfield__input
       {:type "text"
        :style {:width "100%"}
        :value (:title @drawing)
        :on-blur save
        :on-change
        (fn title-changed [e]
          (swap! drawing assoc :title (.. e -target -value)))}]]]]
   [:div
    ;; http://alistapart.com/article/creating-intrinsic-ratios-for-video
    {:style {:position "relative"
             :height 0
             :padding-bottom "100%"
             :border "1px solid black"}
     :ref (fn [elem]
            (when elem
              ;; TODO: why is this called so much???
              (reset! container elem)))}
    [:svg
     (merge-with
       merge
       {:view-box (string/join " " (concat [0 0] @dims))
        :style {:position "absolute"
                :top 0
                :width "100%"
                :height "100%"
                :-webkit-touch-callout "none"
                :-webkit-user-select "none"
                :-khtml-user-select "none"
                :-moz-user-select "none"
                :-ms-user-select "none"
                :user-select "none"}}
       (case @mode
         ::edit
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

         ::erase
         {:style {:cursor "cell"}}

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
                :style {:pointer-events "none"}
                :width "100%"
                :height "100%"
                :opacity 0.3}])
     [paths drawing mode save]]]
   [:textarea
    {:rows 5
     :style {:width "100%"
             :box-sizing "border-box"}
     :value @notes
     :on-blur save
     :on-change
     (fn notes-entered [e]
       (swap! drawing assoc :notes (.. e -target -value)))}]])

(defn draw [drawing save]
  ;; TODO: intial state should be set, but can't deref drawing hmmmm.
  ;; also should probably save after undo/redo?
  (let [history (atom {:index -1
                       :values []})
        dims (ratom/reaction (:dims @drawing default-dims))
        notes (ratom/reaction (:notes @drawing))
        img (reagent/atom nil)
        pen-down? (reagent/atom false)
        mode (reagent/atom ::draw)
        selected (reagent/atom nil)
        container (atom nil)
        save (fn []
               (swap! history new-state @drawing)
               (when save (save)))
        start-path
        (fn start-path [e]
          (when (not= (.-buttons e) 0)
            (reset! pen-down? true)
            (let [[x y] (xy e @dims @container)]
              (swap! drawing update :svg conj [:path {:d ['M x y 'L x y]}]))))
        continue-path
        (fn continue-path [e]
          (when @pen-down?
            (let [[x y] (xy e @dims @container)]
              (swap! drawing update :svg #(update-in % [(dec (count %)) 1 :d] conj x y)))))
        end-path
        (fn end-path [e]
          (when @pen-down?
            (when e
              (continue-path e))
            (reset! pen-down? false)
            (when save (save))))
        select
        (fn select [e]
          (reset! selected (.-target e)))
        drag
        (fn drag [e]
          (prn "drag"))
        dropped
        (fn dropped [e]
          (prn "dropped"))]
    [a-draw drawing dims notes history img mode container
     select drag dropped start-path continue-path end-path save]))

(defcard-rg draw-card
  [draw (reagent/atom {:svg []}) nil])

(defn prepare-svg [tag properties elems]
  (into
    [tag (merge {:fill "none"
                 :stroke "black"
                 :stroke-width 4
                 :stroke-linecap "round"
                 :stroke-linejoin "round"}
                properties)]
    (for [elem elems]
      (prepare elem ::draw nil nil nil))))

(defn observe [drawing]
  (let [{:keys [dims svg svg-attrs]} (model/parse (js->clj @drawing))]
    [prepare-svg :svg
     (merge
       {:view-box (string/join " " (concat [0 0] (or dims default-dims)))
        :style {:border "1px solid black"
                ;; TODO: moar browzazs
                :-webkit-user-select "none"}}
       svg-attrs)
     svg]))

(defcard-rg view-card
  [observe
   (reagent/atom
     #js {:svg "[[:path {:d [M 50 50 L 100 100]}] [:path {:d [M 200 200 L 300 300]}]]"})])
