(ns algopop.napkindo.firebase
  (:require
    [cljsjs.firebase]
    [cljs.pprint :refer [pprint]]
    [clojure.string :as string]
    [reagent.core :as reagent]
    [reagent.ratom :as ratom]
    [cljs.test]
    [devcards.core]
    [goog.dom.forms :as forms])
  (:require-macros
    [devcards.core :refer [defcard-rg]]))

(def timestamp
  js/firebase.database.ServerValue.TIMESTAMP)

(defn db-ref [path]
  (.ref (js/firebase.database) (string/join "/" path)))

(defn save [path value]
  (.set (db-ref path) value))

(defn push
  ([path] (.push (db-ref path)))
  ([path value] (.push (db-ref path) value)))

(defn delete [path]
  (.remove (db-ref path) (fn [e])))

(defonce user
  (reagent/atom nil))

(defn is-user-equal [google-user firebase-user]
  (and
    firebase-user
    (some
      #(and (= (.-providerId %) js/firebase.auth.GoogleAuthProvider.PROVIDER_ID)
            (= (.-uid %) (.getId (.getBasicProfile google-user))))
      (:providerData firebase-user))))

(defn ^:export onSignIn [google-user]
  (when (not (is-user-equal google-user @user))
    (.catch
      (.signInWithCredential
        (js/firebase.auth)
        (js/firebase.auth.GoogleAuthProvider.credential
          (.-id_token (.getAuthResponse google-user))))
      (fn [error]
        (js/alert error)))))

(defn on-auth []
  (.onAuthStateChanged
    (js/firebase.auth)
    (fn auth-state-changed [firebase-user]
      (let [uid (.-uid firebase-user)
            display-name (.-displayName firebase-user)
            photo-url (.-photoURL firebase-user)
            provider-data (.-providerData firebase-user)]
        (if uid
          (do
            (save ["users" uid "settings"]
                  #js {:photo-url photo-url
                       :display-name display-name})
            (reset! user {:photoURL photo-url
                          :displayName display-name
                          :uid uid
                          :providerData provider-data}))
          (when @user
            (reset! user nil)))))
    (fn auth-error [error]
      (js/alert error))))

(defn init []
  (js/firebase.initializeApp
    #js {:apiKey "AIzaSyCfWhE7p073v_HoFARcqkJIVpA4Z8vMixA"
         :authDomain "napkindo.firebaseapp.com"
         :databaseURL "https://napkindo.firebaseio.com"
         :storageBucket "napkindo.appspot.com"})
  (on-auth))

(defcard-rg save-card
  [:form
   {:on-submit
    (fn [e]
      (.preventDefault e)
      (save ["test"] (forms/getValueByName (.-target e) "test")))}
   [:div "Type a message and save it to Firebase"]
   [:input
    {:type "text"
     :name "test"}]
   [:input
    {:type "submit"}]])

(defn once
  "Retreives the firebase state at path as a ratom that will be set when the state arrives."
  [path]
  (let [a (reagent/atom nil)]
    (.once
      (db-ref path)
      "value"
      (fn received-db [snapshot]
        (reset! a (.val snapshot))))
    a))

(defcard-rg once-card
  "This card will not update on changes, only when you refresh the page."
  (fn []
    (let [a (once ["test"])]
      (fn []
        [:div @a]))))

(defn on
  "Takes a path and a component.
  Component takes a ratom as it's first argument, and optional other arguments.
  The atom derefs to the firebase state at path."
  [path component]
  (let [ref (db-ref path)
        a (reagent/atom nil)]
    (.on ref "value" (fn [x]
                       (reset! a (.val x))))
    (reagent/create-class
      {:display-name "listener"
       :component-will-unmount
       (fn will-unmount-listener [this]
         (.off ref))
       :reagent-render
       (fn render-listener [path component & args]
         (into [component a] args))})))

(defcard-rg on-card
  "Watch the console for messages. While hidden, no changes are listened to."
  (let [show? (reagent/atom true)]
    (fn []
      [:div
       [:button
        {:on-click
         (fn [e]
           (swap! show? not))}
        (if @show?
          "hide"
          "show")]
       (when @show?
         [on ["test"]
          (fn [a]
            (println "Received change" @a)
            [:div @a])])])))

(defn sign-in []
  ;; TODO: use Credential for mobile.
  (.signInWithRedirect
    (js/firebase.auth.)
    (js/firebase.auth.GoogleAuthProvider.)))

(defn sign-out []
  ;; TODO: add then/error handlers
  (.signOut (js/firebase.auth))
  (reset! user nil))

#_(defn save-db [uid]
  (.set
    (db-ref ["users" uid "db"])
    (pr-str @(or (@db/conns uid)
                 (db/init uid)))))

(defn dissoc-in
  "Dissociates an entry from a nested associative structure returning a new
  nested structure. keys is a sequence of keys. Any empty maps that result
  will not be present in the new structure."
  [m [k & ks :as keys]]
  (if ks
    (if-let [nextmap (get m k)]
      (let [newmap (dissoc-in nextmap ks)]
        (if (seq newmap)
          (assoc m k newmap)
          (dissoc m k)))
      m)
    (dissoc m k)))

(defn with-refs
  "Takes a component that will render with an atom, on, off and args.
  on and off take paths to listen/unlisten to.
  The atom derefs to the firebase state for listened to paths."
  [component & args]
  (let [refs (reagent/atom {})
        a (reagent/atom {})
        on (fn on [path]
             (when-not (@refs path)
               (swap! refs assoc path
                      (doto (db-ref path)
                        (.on "value" (fn [x]
                                       (swap! a assoc-in path (.val x))))))))
        off (fn off [path]
              (when-let [ref (@refs path)]
                (.off ref))
              (swap! refs dissoc path)
              (swap! a dissoc-in path))]
    (reagent/create-class
      {:display-name "listener"
       :component-will-unmount
       (fn will-unmount-listener [this]
         (doseq [ref (vals @refs)]
           (.off ref)))
       :reagent-render
       (fn render-listener [component & args]
         (into [component a on off] args))})))

(defcard-rg with-refs-card
  "Takes a component that will render with an atom, on, off and args.
  on and off take paths to listen/unlisten to.
  The atom derefs to the firebase state for listened to paths."
  (let [show? (reagent/atom true)]
    (fn []
      [:div
       [:button {:on-click (fn [e] (swap! show? not))} (if @show? "hide" "show")]
       (when @show?
         [with-refs
          (fn listening-component [a on off]
            (into
              [:div
               [:div (pr-str @a)]]
              (mapcat
                (fn [path]
                  [[:button {:on-click (fn [e] (on path))} (str "on " (pr-str path))]
                   [:button {:on-click (fn [e] (off path))} (str "off " (pr-str path))]])
                [["test"] ["test2"]])))])])))

(defn with-refs-only
  [add clear component & args]
  (let [refs (reagent/atom {})
        on (fn on [path]
             (when-not (@refs path)
               (swap! refs assoc path
                      (doto (db-ref path)
                        (.on "value" (fn [x]
                                       (add path (.val x))))))))
        off (fn off [path]
              (when-let [ref (@refs path)]
                (.off ref))
              (swap! refs dissoc path)
              (clear path))]
    (reagent/create-class
      {:display-name "listener"
       :component-will-unmount
       (fn will-unmount-listener [this]
         (doseq [ref (vals @refs)]
           (.off ref)))
       :reagent-render
       (fn render-listener [add clear component & args]
         (into [component on off] args))})))
