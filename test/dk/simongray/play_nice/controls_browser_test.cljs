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

(deftest a-pointer-on-the-seek-bar
  (let [^js bar (js/document.createElement "div")
        seeks   (atom [])]
    (set! (.. bar -style -cssText) "position:fixed;left:100px;top:0;width:200px;height:10px")
    (.appendChild js/document.body bar)
    (.addEventListener bar "pointerdown"
                       #(swap! seeks conj (controls/slider-seek % {:duration 3600})))
    (.dispatchEvent bar (js/PointerEvent. "pointerdown" #js {:clientX 150 :clientY 5 :bubbles true}))
    (is (= [{:action :seekto :seek-time 900}] @seeks))
    (.remove bar)))

(deftest the-language-of-the-page
  (let [^js html js/document.documentElement
        lang     (.-lang html)]
    (set! (.-lang html) "da")
    (is (= "12 minutter og 30 sekunder" (controls/value-text 750)))
    (is (= "12 minutter og 30 sekunder"
           (:aria-valuetext (controls/slider-attributes {:current-time 750 :duration 3600}))))
    (is (= "12 minutes, 30 seconds" (controls/value-text 750 "en")) "the locale you give first")
    (set! (.-lang html) "not a language!")
    (is (= (controls/value-text 750 js/undefined) (controls/value-text 750))
        "the browser's, where the page's lang is no language")
    (set! (.-lang html) lang)))

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
                  (controls/listen! bar v handlers {:keymap controls/slider-keys})]
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
