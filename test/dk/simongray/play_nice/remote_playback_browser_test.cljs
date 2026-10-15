(ns dk.simongray.play-nice.remote-playback-browser-test
  "Remote Playback in a browser, without a click, and without a device as
  far as CI knows."
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.play-nice.remote-playback :as remote]
            [dk.simongray.play-nice.test-support
             :refer [muted-video next-event run silence]]))

(def supported?
  "Whether the browser has Remote Playback."
  (some? (.-remote (js/document.createElement "video"))))

(defn wait
  "A promise that resolves after `ms` milliseconds."
  [ms]
  (js/Promise. (fn [resolve _] (js/setTimeout resolve ms))))

(deftest watching
  (let [^js v  (muted-video)
        states (atom [])
        stop!  (remote/watch! v (partial swap! states conj))]
    (set! (.-src v) (silence))
    (run #(wait 1500)
         #(let [{:keys [availability state]} (last @states)]
            (is (= :disconnected state))
            (if supported?
              (is (#{:available :unavailable :unknown} availability)
                  "as the browser tells, with a device on the network or not")
              (is (= :unavailable availability))))
         #(do (stop!)
              (.removeAttribute v "src")))))

;; with a source, since WebKit refuses an element without one as
;; NotSupportedError
(deftest prompting-without-a-click
  (let [^js v        (muted-video)
        ^js disabled (muted-video)]
    (set! (.-src v) (silence))
    (remote/disable! disabled)
    (run #(next-event v "loadedmetadata")
         #(.then (remote/prompt! v)
                 (fn [{:keys [refused]}]
                   ;; Chrome sometimes answers that the picker was dismissed
                   (is (if supported?
                         (#{:not-allowed :dismissed} refused)
                         (= :not-supported refused)))))
         #(.then (remote/prompt! disabled)
                 (fn [{:keys [refused]}]
                   (is (= (if supported? :disabled :not-supported) refused)))))))

(deftest disabling
  (let [^js v (muted-video)]
    (.setAttribute v "x-webkit-airplay" "allow")
    (is (nil? (remote/disable! v)))
    (is (.hasAttribute v "disableremoteplayback"))
    (is (not (.hasAttribute v "x-webkit-airplay"))
        "which on an iPhone would let the video use AirPlay all the same")
    (when supported?
      (is (true? (.-disableRemotePlayback v))))))
