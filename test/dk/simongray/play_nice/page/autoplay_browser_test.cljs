(ns dk.simongray.play-nice.page.autoplay-browser-test
  "The page's autoplay policy and user activation, in a page that nobody
  clicks."
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.play-nice.page.autoplay :as autoplay]))

(deftest the-policy-of-the-page
  (let [^js navigator js/navigator
        policy        (autoplay/policy :mediaelement)]
    (if (fn? (.-getAutoplayPolicy navigator))
      (is (#{:allowed :allowed-muted :disallowed} policy))
      (is (nil? policy) "where the browser can't tell"))
    (is (= policy (autoplay/policy (js/document.createElement "audio"))))
    (is (nil? (autoplay/policy :video)) "a kind that doesn't exist")))

(deftest no-activation-from-the-page
  (let [active? (some-> js/navigator .-userActivation .-isActive)
        calls   (atom 0)
        stop!   (autoplay/on-activation! #(swap! calls inc))]
    (.dispatchEvent js/window (js/KeyboardEvent. "keydown" #js {:key "a"}))
    (.dispatchEvent js/window (js/MouseEvent. "mousedown"))
    ;; Playwright's Firefox leaves the page activated after earlier tests
    (if active?
      (is (= 1 @calls) "at once, where the page has activation")
      (is (= 0 @calls) "since events of the page's own don't activate it"))
    (stop!)))
