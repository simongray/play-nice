(ns dk.simongray.play-nice.autoplay-browser-test
  "The page's autoplay policy and user activation, in a page that nobody
  clicks."
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.play-nice.autoplay :as autoplay]))

(deftest the-policy-of-the-page
  (let [^js navigator js/navigator
        policy        (autoplay/policy :mediaelement)]
    (if (fn? (.-getAutoplayPolicy navigator))
      (is (#{:allowed :allowed-muted :disallowed} policy))
      (is (nil? policy) "where the browser can't tell"))
    (is (= policy (autoplay/policy (js/document.createElement "audio"))))
    (is (nil? (autoplay/policy :video)) "a kind that doesn't exist")))

(deftest no-activation-from-the-page
  (let [calls (atom 0)
        stop! (autoplay/on-activation! #(swap! calls inc))]
    (.dispatchEvent js/window (js/KeyboardEvent. "keydown" #js {:key "a"}))
    (.dispatchEvent js/window (js/MouseEvent. "mousedown"))
    (is (= 0 @calls) "since events of the page's own don't activate it")
    (stop!)))
