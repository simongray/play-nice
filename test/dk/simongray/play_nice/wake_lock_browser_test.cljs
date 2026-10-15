(ns dk.simongray.play-nice.wake-lock-browser-test
  "The screen kept on in a browser, with the browser's own wake lock, which
  a headless browser may grant or refuse."
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.play-nice.wake-lock :as wake-lock]
            [dk.simongray.play-nice.test-support
             :refer [muted-video run silence]]))

(deftest holding-the-screen
  (let [^js v (muted-video)
        stop! (atom nil)]
    (set! (.-src v) (silence))
    (set! (.-loop v) true)
    (.appendChild js/document.body v)
    (run #(.play v)
         #(reset! stop! (wake-lock/hold-while-playing! v))
         #(js/Promise. (fn [resolve _] (js/setTimeout resolve 300)))
         #(.pause v)
         #(is (nil? (@stop!)) "stopped, whether the browser granted it or not")
         #(.remove v))))
