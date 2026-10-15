(ns dk.simongray.play-nice.page.play-in-view-test
  "Autoplay in view on a stand-in for a video, where there's no viewport."
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.play-nice.page.autoplay :as autoplay]))

(defn video
  "A stand-in for a paused video."
  []
  (doto (js/EventTarget.)
    (unchecked-set "paused" true)
    (unchecked-set "muted" true)))

(deftest without-a-viewport
  (let [states (atom [])
        ^js v  (video)
        stop!  (autoplay/play-in-view! v (partial swap! states conj)
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
    (autoplay/play-in-view! (video) (partial swap! states conj)
                            {:reduced-motion true})
    (is (= [{:visible false :playing false :held :reduced-motion}] @states)))
  (is (boolean? (autoplay/reduced-motion?))))
