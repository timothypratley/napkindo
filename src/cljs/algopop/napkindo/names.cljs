(ns algopop.napkindo.names
  (:require [clojure.string :as string]))

(def dictionary
  ["Masterpeice" "Sketch" "Drawing" "Diagram" "Cartoon"
   "Elegant" "Outline" "Etching" "Scratch" "Flowing"
   "Characture" "Mural" "Art" "Expression" "Beauty"
   "Ultimate" "Signature" "Peice" "Flamboyant" "Inspirational"
   "Magnum Opus" "Beautiful" "Prime" "Vibrant" "Vivid"
   "Astonishing" "Breathtaking" "Idea" "Cute" "Pretty"
   "Stunning" "Exquisite" "Fine" "Wonderful" "Handsome"
   "Gorgeous" "Fascinating" "Splendid" "Pleasing" "Charming"
   "Plan" "Suggestion" "Thought" "Layout" "Blueprint"
   "Napkin" "Doodle" "Illustration" "Wow" "Insight"
   "Awesome" "Graph" "Note" "Squiggle" "Brainstorm"])

(defn sketch-name []
  (string/join " " (take (+ 2 (rand-int 3)) (shuffle dictionary))))
