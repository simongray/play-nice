(ns dk.simongray.play-nice.page.tabs-browser-test
  "One player at a time in a browser, with two muted videos in one page,
  which share the origin's Web Locks as two tabs would, and one playback
  across the page and a frame of it, whose window has a BroadcastChannel
  of its own, as another tab has."
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.play-nice.page.tabs :as tabs]
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

(defn frame-window
  "The window of a new frame of the page, of the same origin, which has a
  BroadcastChannel of its own and the origin's Web Locks, as another tab
  has."
  []
  (let [frame (js/document.createElement "iframe")]
    (.appendChild js/document.body frame)
    (.-contentWindow frame)))

(deftest one-playback-across-two-windows
  (let [app      (str "play-nice-test-" (random-uuid))
        other    (frame-window)
        ^js a    (muted-video)
        ^js b    (muted-video)
        id       (constantly "episode-1")
        seen     (atom [])
        detaches (atom [])]
    (doseq [^js v [a b]]
      (set! (.-src v) (silence))
      (set! (.-loop v) true))
    (swap! detaches conj (tabs/attach! a app {:id-fn id :handlers {:pause #(.pause a)}}))
    (swap! detaches conj (tabs/attach! b app {:id-fn id :window other :align? true}))
    (swap! detaches conj (tabs/watch! app #(swap! seen conj %) {:window other}))
    (run #(.play a)
         #(wait 300)
         #(is (= ["episode-1" false] ((juxt :id :paused) (last @seen)))
              "the other window learns what plays")
         #(let [paused (next-event a "pause")]
            (tabs/press! app {:action :pause} {:window other})
            paused)
         #(is (.-paused a) "an action of the other window pauses it where it plays")
         #(do (set! (.-currentTime a) 0.5)
              (tabs/share! a)
              (wait 300))
         #(let [playing (next-event b "playing")]
            (.play b)
            playing)
         ;; for a seek that an engine dropped as the element started, made
         ;; again as it plays
         #(wait 100)
         #(is (< 0.45 (.-currentTime b) 0.95)
              "the other window starts from where the playback is")
         #(do (.pause b)
              (run! (fn [detach!] (detach!)) @detaches)))))
