(ns dk.simongray.play-nice.display.wake-lock-test
  "The screen kept on, on stand-ins for a media element, its page and the
  browser's wake lock."
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.play-nice.display.wake-lock :as wake-lock]
            [dk.simongray.play-nice.test-support :refer [run]]))

(defn browser
  "Stand-ins for a window whose wake lock adds :request and :release to the
  atom `calls`, and a paused media element in a visible page of it."
  [calls]
  (let [^js document (doto (js/EventTarget.)
                       (unchecked-set "visibilityState" "visible"))
        ^js element  (doto (js/EventTarget.)
                       (unchecked-set "paused" true)
                       (unchecked-set "ownerDocument" document))
        request      (fn [_]
                       (swap! calls conj :request)
                       (js/Promise.resolve
                        (doto (js/EventTarget.)
                          (unchecked-set "release"
                                         #(swap! calls conj :release)))))]
    {:window   #js {:navigator #js {:wakeLock #js {:request request}}}
     :document document
     :element  element}))

(defn tick
  "A promise that resolves once the wake lock's promises have settled."
  []
  (js/Promise. (fn [resolve _] (js/setTimeout resolve 0))))

(deftest holding-while-playing
  (let [calls (atom [])
        {:keys [^js window ^js document ^js element]} (browser calls)
        stop! (wake-lock/hold-while-playing! element {:window window})]
    (run tick
         #(is (= [] @calls) "nothing while paused")
         #(do (set! (.-paused element) false)
              (.dispatchEvent element (js/Event. "play")))
         tick
         #(is (= [:request] @calls))
         #(do (set! (.-paused element) true)
              (.dispatchEvent element (js/Event. "pause")))
         #(is (= [:request :release] @calls) "let go at a pause")
         #(do (set! (.-paused element) false)
              (.dispatchEvent element (js/Event. "play")))
         tick
         ;; the browser lets go of the lock while the page is hidden
         #(do (set! (.-visibilityState document) "hidden")
              (.dispatchEvent document (js/Event. "visibilitychange")))
         #(is (= [:request :release :request] @calls) "none while hidden")
         #(do (set! (.-visibilityState document) "visible")
              (.dispatchEvent document (js/Event. "visibilitychange")))
         tick
         #(is (= [:request :release :request] @calls)
              "still held, since no release came")
         #(stop!)
         #(is (= [:request :release :request :release] @calls)
              "let go when stopped"))))

(deftest asking-again-after-the-browser-let-go
  (let [calls    (atom [])
        released (atom nil)
        ^js doc  (doto (js/EventTarget.)
                   (unchecked-set "visibilityState" "visible"))
        ^js el   (doto (js/EventTarget.)
                   (unchecked-set "paused" false)
                   (unchecked-set "ownerDocument" doc))
        request  (fn [_]
                   (swap! calls conj :request)
                   (let [^js s (doto (js/EventTarget.)
                                 (unchecked-set "release" (constantly nil)))]
                     (reset! released #(.dispatchEvent s (js/Event. "release")))
                     (js/Promise.resolve s)))
        window   #js {:navigator #js {:wakeLock #js {:request request}}}]
    (wake-lock/hold-while-playing! el {:window window})
    (run tick
         #(is (= [:request] @calls) "at once, for a playing element")
         #(do (set! (.-visibilityState doc) "hidden")
              (@released)
              (set! (.-visibilityState doc) "visible")
              (.dispatchEvent doc (js/Event. "visibilitychange")))
         tick
         #(is (= [:request :request] @calls) "and again when the page shows"))))

(deftest without-a-wake-lock
  (is (nil? ((wake-lock/hold-while-playing! (js/EventTarget.) {:window #js {}})))))
