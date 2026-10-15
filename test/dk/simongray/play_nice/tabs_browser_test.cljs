(ns dk.simongray.play-nice.tabs-browser-test
  "One player at a time in a browser, with two muted videos in one page,
  which share the origin's Web Locks as two tabs would."
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.play-nice.tabs :as tabs]
            [dk.simongray.play-nice.test-support
             :refer [muted-video next-event run silence]]))

(defn held
  "A promise of the names of the locks that the page holds."
  []
  (.then (.query ^js (.-locks js/navigator))
         (fn [^js snapshot] (set (map #(.-name ^js %) (.-held snapshot))))))

(defn wait
  "A promise that resolves after `ms` milliseconds."
  [ms]
  (js/Promise. (fn [resolve _] (js/setTimeout resolve ms))))

(deftest one-player-at-a-time
  (let [app      (str "play-nice-test-" (random-uuid))
        ^js a    (muted-video)
        ^js b    (muted-video)
        detaches (atom [])]
    (doseq [^js v [a b]]
      (set! (.-src v) (silence))
      (set! (.-loop v) true)
      (swap! detaches conj (tabs/attach! v app)))
    (run #(.play a)
         #(wait 300)
         #(.then (held)
                 (fn [names] (is (contains? names app) "held while it plays")))
         #(let [paused (next-event a "pause")]
            (.play b)
            paused)
         #(is (and (.-paused a) (not (.-paused b)))
              "the other player took the lock, and paused the first")
         #(.pause b)
         #(wait 300)
         #(.then (held)
                 (fn [names] (is (not (contains? names app)) "let go at a pause")))
         #(.play a)
         #(.pause a)
         #(.play a)
         #(wait 300)
         #(is (not (.-paused a)) "a quick pause and play doesn't steal from itself")
         #(do (.pause a)
              (run! (fn [detach!] (detach!)) @detaches)))))
