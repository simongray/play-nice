(ns dk.simongray.play-nice.viewport-test
  "Autoplay in view on a stand-in for a video, where there's no viewport."
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.play-nice.viewport :as viewport]))

(defn video
  "A stand-in for a paused video."
  []
  (doto (js/EventTarget.)
    (unchecked-set "paused" true)
    (unchecked-set "muted" true)))

(deftest without-a-viewport
  (let [states (atom [])
        ^js v  (video)
        stop!  (viewport/autoplay! v (partial swap! states conj)
                                   {:reduced-motion false})]
    (is (= [{:visible false :playing false}] @states))
    (set! (.-paused v) false)
    (.dispatchEvent v (js/Event. "play"))
    (set! (.-paused v) true)
    (.dispatchEvent v (js/Event. "pause"))
    (is (= {:visible false :playing false :held :visitor} (last @states))
        "a pause that the call didn't make")
    (is (nil? (stop!)))))

(deftest reduced-motion
  (let [states (atom [])]
    (viewport/autoplay! (video) (partial swap! states conj)
                        {:reduced-motion true})
    (is (= [{:visible false :playing false :held :reduced-motion}] @states)))
  (is (boolean? (viewport/reduced-motion?))))
