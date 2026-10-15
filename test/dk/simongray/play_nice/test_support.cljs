(ns dk.simongray.play-nice.test-support
  "Helpers for the async tests: running steps one after another, waiting
  for an element's next event, and media files made in the test."
  (:require [clojure.test :refer [async is]]))

(defn within
  "The `promise`, or a promise rejected after `seconds` without an answer,
  e.g. where an engine never settles a fullscreen request, named by the
  `label`, so that a test fails rather than hangs."
  [seconds label promise]
  (js/Promise.race
   #js [promise
        (js/Promise. (fn [_ reject]
                       (js/setTimeout #(reject (str label ": no answer in " seconds " s"))
                                      (* 1000 seconds))))]))

(defn run
  "Run the steps `fs` one after another as an async test. Each step is a
  function of no arguments that returns a value or a promise, which fails
  the test after 12 seconds without an answer, so that a hang names its
  step rather than stop the browser's other tests.

  A deftest runs only the async test that it returns, so it calls `run`
  once, as its last form."
  [& fs]
  (async done
    (-> (reduce (fn [p [i f]]
                  (.then p (fn [_]
                             (within 12 (str "step " (inc i))
                                     (js/Promise.resolve (f))))))
                (js/Promise.resolve nil)
                (map-indexed vector fs))
        (.catch (fn [e] (is false (str "rejected: " e))))
        (.finally done))))

(defn next-event
  "A promise of the next event named `event` at the `element`. It rejects
  after 10 seconds without one, so that a test fails rather than hangs,
  and the browser's other tests still report."
  [^js element event]
  (js/Promise. (fn [resolve reject]
                 (.addEventListener element event resolve #js {:once true})
                 (js/setTimeout #(reject (str "no " event " in 10 s")) 10000))))

(defn silence
  "An object URL of a WAV file that holds a second of silence, as 8000
  samples of 8 bits."
  []
  (let [n      8000
        buffer (js/ArrayBuffer. (+ 44 n))
        view   (js/DataView. buffer)
        ascii! (fn [offset s]
                 (doseq [[i c] (map-indexed vector s)]
                   (.setUint8 view (+ offset i) (.charCodeAt c 0))))
        u32!   #(.setUint32 view %1 %2 true)
        u16!   #(.setUint16 view %1 %2 true)]
    (ascii! 0 "RIFF")
    (u32! 4 (+ 36 n))
    (ascii! 8 "WAVE")
    (ascii! 12 "fmt ")
    (u32! 16 16)
    (u16! 20 1)                               ; PCM
    (u16! 22 1)                               ; mono
    (u32! 24 8000)                            ; samples per second
    (u32! 28 8000)                            ; bytes per second
    (u16! 32 1)                               ; bytes per sample
    (u16! 34 8)                               ; bits per sample
    (ascii! 36 "data")
    (u32! 40 n)
    (.fill (js/Uint8Array. buffer 44 n) 128)  ; silence, as 8 bits are unsigned
    (js/URL.createObjectURL (js/Blob. #js [buffer] #js {:type "audio/wav"}))))

(defn garbage
  "An object URL of a file that claims to be WAV but isn't, so that no
  browser plays it."
  []
  (js/URL.createObjectURL (js/Blob. #js ["not audio"] #js {:type "audio/wav"})))

(defn muted-video
  "A muted video element, since Chrome plays only a muted video without a
  click, which plays inline, since an iPhone plays others fullscreen."
  []
  (doto (js/document.createElement "video")
    (set! -muted true)
    (set! -playsInline true)))

(defn canvas-video
  "A muted video in the page of a canvas's stream, which has a video track,
  unlike the WAV of silence. The canvas draws for five seconds, since
  WebKit loads no metadata from a canvas that doesn't."
  []
  (let [^js canvas (js/document.createElement "canvas")
        ^js g      (.getContext canvas "2d")
        ^js video  (muted-video)
        n          (atom 0)
        draw!      #(do (set! (.-fillStyle g) (if (even? (swap! n inc)) "#000" "#333"))
                        (.fillRect g 0 0 320 180))
        id         (js/setInterval draw! 50)]
    (js/setTimeout #(js/clearInterval id) 5000)
    (set! (.-width canvas) 320)
    (set! (.-height canvas) 180)
    (when (fn? (.-captureStream canvas))
      (set! (.-srcObject video) (.captureStream canvas 10)))
    (.appendChild js/document.body video)
    video))
