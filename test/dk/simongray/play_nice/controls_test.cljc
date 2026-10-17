(ns dk.simongray.play-nice.controls-test
  "The keyboard's and the seek bar's pure functions, and in ClojureScript
  listen! on stand-ins for the player and its media element."
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.play-nice.controls :as controls]))

(deftest the-keys
  (is (= {:action :play} (controls/play-or-pause {:paused true})))
  (is (= {:action :pause} (controls/play-or-pause {:paused false})))
  (let [state {:current-time 100 :duration 3600}
        press #((get controls/slider-keys %) state)]
    (is (= {:action :seekto :seek-time 101} (press "ArrowRight")))
    (is (= {:action :seekto :seek-time 99} (press "ArrowDown")))
    (is (= {:action :seekto :seek-time 115} (press "PageUp")))
    (is (= {:action :seekto :seek-time 85} (press "PageDown")))
    (is (= {:action :seekto :seek-time 0} (press "Home")))
    (is (= {:action :seekto :seek-time 3600} (press "End")))))

#?(:cljs
   (deftest seeking-at-the-pointer
     (let [bar   #js {:getBoundingClientRect (fn [] #js {:left 100 :width 200})}
           at    #(controls/slider-seek #js {:currentTarget bar :clientX %1}
                                        {:duration %2})]
       (is (= {:action :seekto :seek-time 900} (at 150 3600)))
       (is (= {:action :seekto :seek-time 0} (at 50 3600)) "before the bar, its start")
       (is (= {:action :seekto :seek-time 3600} (at 400 3600)) "past it, its end")
       (is (nil? (at 150 nil)) "nothing before the metadata")
       (is (nil? (at 150 ##Inf)) "nor in a live stream"))))

(deftest the-value-text
  (is (= "12 minutes, 30 seconds" (controls/value-text 750 "en")))
  (is (= "1 hour, 5 seconds" (controls/value-text 3605.9 "en")))
  (is (= "1 minute" (controls/value-text 60 "en")))
  (is (= "0 seconds" (controls/value-text 0 "en")))
  (is (= "0 seconds" (controls/value-text -5 "en")) "nothing before the start")
  (is (= "0 seconds" (controls/value-text ##NaN "en")) "no position")
  (is (= "1 hour, 1 minute, 1 second" (controls/value-text 3661 "!"))
      "English, where the browser can't format the locale"))

(deftest a-seek-bar-of-your-own
  (is (= {:role           "slider"
          :aria-valuemin  0
          :aria-valuemax  3600
          :aria-valuenow  750
          :aria-valuetext "12 minutes, 30 seconds"}
         (controls/slider-attributes {:current-time 750.6 :duration 3600.4} "en")))
  (is (not (contains? (controls/slider-attributes {:current-time 5 :duration nil})
                      :aria-valuemax))
      "no maximum before the metadata")
  (is (not (contains? (controls/slider-attributes {:current-time 5 :duration ##Inf})
                      :aria-valuemax))
      "nor for a live stream"))

(deftest a-range-input
  (is (= {:min            0
          :max            3600
          :step           1
          :value          750
          :aria-valuetext "12 minutes, 30 seconds"}
         (controls/range-attributes {:current-time 750.6 :duration 3600.4} "en")))
  (is (= [0 0] ((juxt :max :value) (controls/range-attributes {:current-time nil :duration nil})))
      "nowhere to seek before the metadata")
  (is (= [630 630] ((juxt :max :value) (controls/range-attributes {:current-time 630 :duration nil})))
      "but a position that the browser keeps, where the episode resumes")
  (is (= 5 (:max (controls/range-attributes {:current-time 5 :duration ##Inf})))
      "nor in a live stream"))

#?(:cljs
   (deftest seeking-with-a-range-input
     (let [seek #(controls/range-seek #js {:type %1 :target #js {:valueAsNumber %2}})]
       (is (= {:action :seekto :seek-time 1830 :fast-seek true} (seek "input" 1830))
           "near the point while it drags")
       (is (= {:action :seekto :seek-time 1830 :fast-seek false} (seek "change" 1830))
           "and on it at the end"))))

#?(:cljs
   (defn keydown
     "A keydown event of the `key`, with the `props` of a KeyboardEvent, e.g.
     {:repeat true}."
     [key & {:as props}]
     (let [^js e (js/Event. "keydown" #js {:cancelable true})]
       (doseq [[k v] (assoc props :key key)]
         (unchecked-set e (name k) v))
       e)))

#?(:cljs
   (deftest listening
     (let [pressed  (atom [])
           handlers {:play         #(swap! pressed conj %)
                     :pause        #(swap! pressed conj %)
                     :seekforward  #(swap! pressed conj %)}
           ^js el   #js {:paused true :currentTime 0}
           ^js pad  (js/EventTarget.)
           stop!    (controls/listen! pad el handlers)
           press!   #(let [^js e (apply keydown %&)]
                       (.dispatchEvent pad e)
                       (.-defaultPrevented e))]
       (is (true? (press! " ")))
       (set! (.-paused el) false)
       (is (true? (press! "k")))
       (is (true? (press! "l")))
       (is (= [{:action :play} {:action :pause} {:action :seekforward}] @pressed))
       (is (false? (press! "j")) "no handler, so the browser's own")
       (is (false? (press! "x")))
       (is (false? (press! "l" :ctrlKey true)) "a shortcut of the browser")
       (is (true? (press! "l" :repeat true)) "a held seek repeats")
       (is (false? (press! " " :repeat true)) "but a held space doesn't")
       (is (= 4 (count @pressed)))
       (is (nil? (stop!)))
       (is (false? (press! " ")) "until it stops")
       (is (nil? ((controls/listen! #js {} el handlers)))
           "and nothing without a scope"))))
