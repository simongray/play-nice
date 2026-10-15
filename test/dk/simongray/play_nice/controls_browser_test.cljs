(ns dk.simongray.play-nice.controls-browser-test
  "Keyboard shortcuts in a browser, in a player with a button, a field and
  a seek bar of its own."
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.play-nice.controls :as controls]
            [dk.simongray.play-nice.test-support :refer [muted-video]]))

(defn player
  "A player element that holds a button, a field and a seek bar."
  []
  (let [^js p (js/document.createElement "div")]
    (set! (.-innerHTML p)
          (str "<button>Play</button><input type=text>"
               "<div role=slider tabindex=0></div>"
               "<div contenteditable=true></div>"))
    (.appendChild js/document.body p)
    p))

(deftest keys-in-a-player
  (let [pressed  (atom [])
        handlers {:play         #(swap! pressed conj %)
                  :pause        #(swap! pressed conj %)
                  :seekforward  #(swap! pressed conj %)
                  :seekto       #(swap! pressed conj %)}
        ^js p    (player)
        ^js v    (muted-video)
        ^js bar  (.querySelector p "[role=slider]")
        stops    [(controls/listen! p v handlers)
                  (controls/listen! bar v handlers {:keymap controls/seek-bar-keys})]
        press!   (fn [selector key]
                   (let [^js e (js/KeyboardEvent. "keydown"
                                                  #js {:key        key
                                                       :bubbles    true
                                                       :cancelable true})]
                     (.dispatchEvent (.querySelector p selector) e)
                     (.-defaultPrevented e)))]
    (is (false? (press! "button" " ")) "a space presses the button instead")
    (is (true? (press! "button" "l")))
    (is (false? (press! "input" "k")) "typing in a field")
    (is (false? (press! "[contenteditable]" "l")))
    (is (true? (press! "[role=slider]" "ArrowRight")))
    (is (true? (press! "[role=slider]" " ")))
    (is (= [{:action :seekforward}
            {:action :seekto :seek-time 1}
            {:action :play}]
           @pressed)
        "the arrow seeks the bar by a second, rather than skip the player")
    (run! #(%) stops)
    (.remove p)))
