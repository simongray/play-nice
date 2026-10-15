(ns dk.simongray.play-nice.display.fullscreen-browser-test
  "Fullscreen refused in a browser, for a player that isn't in the page,
  which every engine refuses alike. A test that entered fullscreen would
  leave WebKit on Linux unable to play in the tests after it, and the
  engines let a page in without a click unpredictably under Playwright."
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.play-nice.display.fullscreen :as fullscreen]
            [dk.simongray.play-nice.test-support
             :refer [muted-video next-event run silence within]]))

(defn player
  "A player outside the page that holds a video, of the WAV of silence if
  `src`."
  [src]
  (let [^js p (js/document.createElement "div")
        ^js v (muted-video)]
    (when src
      (set! (.-src v) (silence)))
    (.appendChild p v)
    p))

(deftest refusing-a-player-outside-the-page
  (let [^js p  (player true)
        ^js v  (.querySelector p "video")
        states (atom [])
        opts   {:video v :orientation :landscape}
        stop!  (fullscreen/watch! p (partial swap! states conj) opts)]
    (run #(next-event v "loadedmetadata")
         #(.then (within 5 "enter!" (fullscreen/enter! p opts))
                 (fn [answer]
                   (is (= {:refused :not-allowed} answer)
                       "in the page or, on an iPhone, in its player")))
         #(.then (within 5 "exit!" (fullscreen/exit! p opts))
                 (fn [answer] (is (= {:fullscreen false} answer))))
         #(is (= [{:fullscreen false}] @states))
         stop!)))

(deftest a-video-without-its-metadata
  (let [^js p (player false)
        ^js v (.querySelector p "video")]
    (run #(.then (within 5 "enter!" (fullscreen/enter! p {:video v}))
                 (fn [{:keys [refused]}]
                   (is (= (if (fn? (.-requestFullscreen p))
                            :not-allowed
                            :no-metadata)
                          refused)
                       "which an iPhone can't show in its player"))))))
