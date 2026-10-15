(ns dk.simongray.play-nice.fullscreen-browser-test
  "Fullscreen in a browser without a click, which every engine refuses,
  and an iPhone, which has only a video's own fullscreen."
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.play-nice.fullscreen :as fullscreen]
            [dk.simongray.play-nice.test-support
             :refer [muted-video next-event run silence within]]))

(defn player
  "A player in the page that holds a video of the WAV of silence."
  []
  (let [^js p (js/document.createElement "div")
        ^js v (muted-video)]
    (set! (.-src v) (silence))
    (.appendChild p v)
    (.appendChild js/document.body p)
    p))

(deftest entering-without-a-click
  (let [^js p   (player)
        ^js v   (.querySelector p "video")
        states  (atom [])
        opts    {:video v :orientation :landscape}
        stop!   (fullscreen/watch! p (partial swap! states conj) opts)]
    (run #(next-event v "loadedmetadata")
         #(.then (within 5 "enter!" (fullscreen/enter! p opts))
                 (fn [answer]
                   (if (:fullscreen answer)
                     ;; Playwright's WebKit, which lets a young page in
                     ;; without a click, and has no lock
                     (is (= {:fullscreen  :element
                             :orientation {:refused :not-supported}}
                            answer))
                     (is (= {:refused :not-allowed} answer)
                         "in the page or, on an iPhone, in its player"))))
         #(.then (within 5 "exit!" (fullscreen/exit! p opts))
                 (fn [answer] (is (= {:fullscreen false} answer))))
         #(js/Promise. (fn [resolve _] (js/setTimeout resolve 300)))
         #(is (= {:fullscreen false} (fullscreen/state p opts) (last @states)))
         #(do (stop!)
              (.remove p)))))

(deftest a-video-without-its-metadata
  (let [^js p (js/document.createElement "div")
        ^js v (muted-video)]
    (.appendChild p v)
    (.appendChild js/document.body p)
    (run #(.then (within 5 "enter!" (fullscreen/enter! p {:video v}))
                 (fn [{:keys [refused] :as answer}]
                   (if (fn? (.-requestFullscreen p))
                     ;; Chrome and Firefox sometimes let a page back in
                     ;; without a click, soon after it has left
                     (is (or (= :element (:fullscreen answer))
                             (= :not-allowed refused))
                         "the page's own fullscreen, which needs no video")
                     (is (= :no-metadata refused)
                         "which an iPhone can't show in its player"))))
         #(within 5 "exit!" (fullscreen/exit! p {:video v}))
         #(.remove p))))
