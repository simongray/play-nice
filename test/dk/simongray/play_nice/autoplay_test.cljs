(ns dk.simongray.play-nice.autoplay-test
  "The autoplay policy and user activation, on a stand-in for the window."
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.play-nice.autoplay :as autoplay]))

(defn window
  "A stand-in for a window: an EventTarget whose navigator is `navigator`."
  [navigator]
  (doto (js/EventTarget.)
    (unchecked-set "navigator" navigator)))

(deftest the-policy
  (let [asked   (atom [])
        firefox {:window (window #js {:getAutoplayPolicy
                                      (fn [target]
                                        (swap! asked conj target)
                                        "allowed-muted")})}
        audio   #js {}]
    (is (= :allowed-muted (autoplay/policy :mediaelement firefox)))
    (is (= :allowed-muted (autoplay/policy audio firefox)))
    (is (= ["mediaelement" audio] @asked) "the kind as a string")
    (is (nil? (autoplay/policy :mediaelement {:window (window #js {})}))
        "where the browser can't tell")
    (is (nil? (autoplay/policy :video
                               {:window (window #js {:getAutoplayPolicy
                                                     #(throw (js/TypeError.))})}))
        "nor for a kind it doesn't know")
    (is (nil? (autoplay/policy :mediaelement {:window #js {}}))
        "nor without a navigator")))

(deftest activation
  (let [calls      (atom 0)
        activation #js {:isActive false}
        opts       {:window (window #js {:userActivation activation})}
        press!     #(.dispatchEvent ^js (:window opts) (js/Event. %))
        stop!      (autoplay/on-activation! #(swap! calls inc) opts)]
    (press! "pointerdown")
    (is (= 0 @calls) "an event that didn't activate the page")
    (set! (.-isActive activation) true)
    (press! "pointerup")
    (is (= 1 @calls))
    (press! "keydown")
    (is (= 1 @calls) "only once")
    (is (nil? (stop!)))
    (autoplay/on-activation! #(swap! calls inc) opts)
    (is (= 2 @calls) "at once, where the page has activation")))

(deftest stopping-and-guessing
  (let [calls  (atom 0)
        opts   {:window (window #js {})}
        press! #(.dispatchEvent ^js (:window opts) (js/Event. %))]
    ((autoplay/on-activation! #(swap! calls inc) opts))
    (press! "keydown")
    (is (= 0 @calls) "after it stopped")
    (autoplay/on-activation! #(swap! calls inc) opts)
    (press! "mousedown")
    (is (= 1 @calls) "any input, where the browser can't tell")
    (is (nil? ((autoplay/on-activation! #(swap! calls inc))))
        "and nothing in Node")))
