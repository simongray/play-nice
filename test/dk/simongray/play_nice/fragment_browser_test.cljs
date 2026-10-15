(ns dk.simongray.play-nice.fragment-browser-test
  "A media fragment in a browser, which starts the media where the
  fragment says: by loadedmetadata in Chrome and Firefox, and at a seek
  after it in WebKit, which on iOS loads nothing before the media plays."
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.play-nice.fragment :as fragment]
            [dk.simongray.play-nice.test-support
             :refer [muted-video next-event run silence]]))

(deftest starting-at-the-fragment
  (let [^js v     (muted-video)
        src       (str (silence) "#t=0.5")
        at-start? #(< (abs (- (:start (fragment/time src)) (.-currentTime v))) 0.05)]
    (set! (.-src v) src)
    (run #(next-event v "loadedmetadata")
         #(when-not (at-start?)
            (let [seeked (next-event v "seeked")]
              (.play v)
              seeked))
         #(is (at-start?) "where fragment/time says the element starts")
         #(.pause v))))
