(ns dk.simongray.play-nice.controls-test
  "The keyboard's and the seek bar's pure functions, and in ClojureScript
  listen! on stand-ins for the player and its media element."
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.play-nice.controls :as controls]))

(deftest the-keys
  (is (= {:action :play} (controls/play-or-pause {:paused true})))
  (is (= {:action :pause} (controls/play-or-pause {:paused false})))
  (let [state {:current-time 100 :duration 3600}
        press #((get controls/seek-bar-keys %) state)]
    (is (= {:action :seekto :seek-time 101} (press "ArrowRight")))
    (is (= {:action :seekto :seek-time 99} (press "ArrowDown")))
    (is (= {:action :seekto :seek-time 115} (press "PageUp")))
    (is (= {:action :seekto :seek-time 85} (press "PageDown")))
    (is (= {:action :seekto :seek-time 0} (press "Home")))
    (is (= {:action :seekto :seek-time 3600} (press "End")))))

(deftest the-value-text
  (is (= "12 minutes, 30 seconds" (controls/value-text 750 "en")))
  (is (= "1 hour, 5 seconds" (controls/value-text 3605.9 "en")))
  (is (= "1 minute" (controls/value-text 60 "en")))
  (is (= "0 seconds" (controls/value-text 0 "en")))
  (is (= "0 seconds" (controls/value-text -5 "en")) "nothing before the start")
  (is (= "0 seconds" (controls/value-text ##NaN "en")) "no position")
  (is (= "1 hour, 1 minute, 1 second" (controls/value-text 3661 "!"))
      "English, where the browser can't format the locale"))

(deftest the-seek-bar
  (is (= {:role           "slider"
          :aria-valuemin  0
          :aria-valuemax  3600
          :aria-valuenow  750
          :aria-valuetext "12 minutes, 30 seconds"}
         (controls/seek-aria {:current-time 750.6 :duration 3600.4} "en")))
  (is (not (contains? (controls/seek-aria {:current-time 5 :duration nil})
                      :aria-valuemax))
      "no maximum before the metadata")
  (is (not (contains? (controls/seek-aria {:current-time 5 :duration ##Inf})
                      :aria-valuemax))
      "nor for a live stream"))

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
