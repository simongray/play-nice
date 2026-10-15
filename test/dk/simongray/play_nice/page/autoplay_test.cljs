(ns dk.simongray.play-nice.page.autoplay-test
  "The autoplay policy and user activation, on a stand-in for the window."
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.play-nice.page.autoplay :as autoplay]
            [dk.simongray.play-nice.test-support :refer [run]]))

(defn window
  "A stand-in for a window: an EventTarget whose navigator is `navigator`."
  [navigator]
  (doto (js/EventTarget.)
    (unchecked-set "navigator" navigator)))

(defn player
  "A stand-in for a media element that plays as the atom `allowed` says,
  :all, :muted or :none, and counts each play in the atom `plays`."
  [allowed plays]
  (let [^js el #js {:muted false}]
    (set! (.-play el)
          (fn []
            (swap! plays inc)
            (if (case @allowed :all true :muted (.-muted el) false)
              (js/Promise.resolve)
              (js/Promise.reject (js/DOMException. "refused" "NotAllowedError")))))
    el))

(defn settle
  "A promise that resolves once the promises before it have settled."
  []
  (js/Promise. (fn [resolve _] (js/setTimeout resolve 10))))

(deftest playing-muted-when-allowed
  (let [plays   (atom 0)
        answers (atom [])
        el      (player (atom :muted) plays)]
    (run #(autoplay/play-when-allowed! el (partial swap! answers conj) {:window (window #js {})})
         settle
         #(is (= [{:playing true :muted true}] @answers))
         #(is (= 2 @plays) "with sound, then muted"))))

(deftest playing-at-the-activation
  (let [allowed    (atom :none)
        answers    (atom [])
        activation #js {:isActive false}
        opts       {:window (window #js {:userActivation activation})}]
    (run #(autoplay/play-when-allowed! (player allowed (atom 0)) (partial swap! answers conj) opts)
         settle
         #(is (= [{:refused :not-allowed :waiting true}] @answers) "waiting")
         #(do (reset! allowed :all)
              (set! (.-isActive activation) true)
              (.dispatchEvent ^js (:window opts) (js/Event. "pointerup")))
         settle
         #(is (= [{:refused :not-allowed :waiting true} {:playing true}] @answers)
              "with sound, after the click"))))

(deftest trying-once-more-only
  (let [plays   (atom 0)
        answers (atom [])
        opts    {:window (window #js {:userActivation #js {:isActive true}})}]
    (run #(autoplay/play-when-allowed! (player (atom :none) plays) (partial swap! answers conj) opts)
         settle
         #(is (= [{:refused :not-allowed :waiting true} {:refused :not-allowed}] @answers)
              "where the page is active and still may not play, e.g. by a setting")
         #(is (= 4 @plays) "two tries, each with sound and muted"))))

(deftest stopping-the-wait
  (let [plays      (atom 0)
        answers    (atom [])
        activation #js {:isActive false}
        opts       {:window (window #js {:userActivation activation}) :muted-fallback false}
        stop!      (atom nil)]
    (run #(reset! stop! (autoplay/play-when-allowed! (player (atom :none) plays)
                                                     (partial swap! answers conj)
                                                     opts))
         settle
         #(is (= 1 @plays) "without the muted try")
         #(is (nil? (@stop!)))
         #(do (set! (.-isActive activation) true)
              (.dispatchEvent ^js (:window opts) (js/Event. "pointerup")))
         settle
         #(is (= [{:refused :not-allowed :waiting true}] @answers) "nothing after it stopped")
         #(is (= 1 @plays)))))

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
