(ns dk.simongray.play-nice.picture-in-picture-browser-test
  "Picture-in-Picture in a browser, which refuses a page without a click,
  except Playwright's Firefox, and which an iOS simulator lacks."
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.play-nice.picture-in-picture :as pip]
            [dk.simongray.play-nice.test-support
             :refer [canvas-video muted-video next-event run silence]]))

(def enabled?
  "Whether the browser has Picture-in-Picture for this page."
  (boolean (.-pictureInPictureEnabled js/document)))

(deftest refusals
  (let [^js audio      (js/document.createElement "audio")
        ^js trackless  (muted-video)
        ^js disabled   (canvas-video)
        refused        (fn [v] (.then (pip/enter! v) #(:refused %)))]
    (set! (.-src trackless) (silence))
    (set! (.-disablePictureInPicture disabled) true)
    (run #(.then (refused audio) (fn [r] (is (= :not-supported r))))
         #(.then (refused trackless)
                 (fn [r]
                   (is (= (if enabled? :no-video :not-supported) r)
                       "the WAV of silence, once its metadata has loaded")))
         #(.then (refused disabled)
                 (fn [r] (is (= (if enabled? :disabled :not-supported) r))))
         #(.then (pip/exit! disabled)
                 (fn [a] (is (= {:picture-in-picture false} a))))
         #(.remove disabled))))

(deftest entering-without-a-click
  (let [^js v  (canvas-video)
        states (atom [])
        stop!  (pip/watch! v (partial swap! states conj))]
    (run #(.then
           (pip/enter! v)
           (fn [answer]
             (if-not (:width answer)
               ;; Playwright's WebKit, which has no Picture-in-Picture,
               ;; refuses the first request of a page with NotSupportedError
               (is (#{:not-allowed :not-supported} (:refused answer))
                   "without a click, which Chrome and WebKit refuse")
               ;; Playwright's Firefox, which lets a young page in without one
               (-> (js/Promise.resolve
                    (when-not (:picture-in-picture (last @states))
                      (next-event v "enterpictureinpicture")))
                   (.then (fn [_]
                            (is (:picture-in-picture (pip/state v)))
                            (is (= answer (last @states))
                                "the answer, as watch! has it")
                            (let [left (next-event v "leavepictureinpicture")]
                              (.then (pip/exit! v) (fn [_] left)))))))))
         #(is (= {:picture-in-picture false} (last @states)))
         #(do (stop!)
              (.remove v)))))
