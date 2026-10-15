(ns dk.simongray.play-nice.schema-browser-test
  "The schemas against the states and answers of a real element."
  (:require [clojure.test :refer [deftest is]]
            [malli.core :as m]
            [dk.simongray.play-nice.fullscreen :as fullscreen]
            [dk.simongray.play-nice.media :as media]
            [dk.simongray.play-nice.picture-in-picture :as picture-in-picture]
            [dk.simongray.play-nice.remote-playback :as remote-playback]
            [dk.simongray.play-nice.schema :as schema]
            [dk.simongray.play-nice.test-support
             :refer [garbage muted-video next-event run silence]]))

(deftest the-states-of-an-element
  (let [^js v   (muted-video)
        ^js bad (muted-video)
        states  (atom [])]
    ((picture-in-picture/watch! v #(swap! states conj [schema/picture-in-picture-state %])))
    ((remote-playback/watch! v #(swap! states conj [schema/remote-playback-state %])))
    (swap! states conj [schema/fullscreen-state (fullscreen/state v)])
    (swap! states conj [schema/media-state (media/state v)])
    (run #(let [loaded (next-event v "loadedmetadata")]
            (media/source! v {:src (silence)})
            loaded)
         #(.then (media/play! v)
                 (fn [answer]
                   (swap! states conj [schema/play-answer answer])
                   (swap! states conj [schema/media-state (media/state v)])
                   (.pause v)))
         #(let [failed (next-event bad "error")]
            (media/source! bad {:src (garbage)})
            (.then (media/play! bad)
                   (fn [answer]
                     (swap! states conj [schema/play-answer answer])
                     failed)))
         #(do (swap! states conj [schema/media-state (media/state bad)])
              (doseq [[s x] @states]
                (is (m/validate s x) (pr-str x)))
              (is (= 8 (count @states)))))))
