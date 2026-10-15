(ns dk.simongray.play-nice.test-support
  "Helpers for the async tests: running steps one after another, waiting
  for an element's next event, and media files made in the test."
  (:require [clojure.test :refer [async is]]))

(defn run
  "Run the steps `fs` one after another as an async test. Each step is a
  function of no arguments that returns a value or a promise.

  A deftest runs only the async test that it returns, so it calls `run`
  once, as its last form."
  [& fs]
  (async done
    (-> (reduce (fn [p f] (.then p (fn [_] (f)))) (js/Promise.resolve nil) fs)
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
