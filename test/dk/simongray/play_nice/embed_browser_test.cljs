(ns dk.simongray.play-nice.embed-browser-test
  "The allow attribute of an iframe in a browser, whose document reports
  what the attribute allows."
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.play-nice.embed :as embed]
            [dk.simongray.play-nice.test-support :refer [next-event run]]))

(defn frame
  "An iframe in the page with the allow attribute for the `policy`."
  [policy]
  (let [^js f (js/document.createElement "iframe")]
    (.setAttribute f "allow" (embed/allow policy))
    (set! (.-srcdoc f) "<!doctype html><p>a player</p>")
    (.appendChild js/document.body f)
    f))

;; A frame from the page's own origin has fullscreen unless the page
;; takes it away, which suits a test without a second origin.
(deftest a-frame-without-fullscreen
  (let [^js f (frame {:fullscreen :none})]
    (run #(next-event f "load")
         #(let [^js d (.-contentDocument f)]
            ;; an iPhone has no fullscreen of an element in the first place
            (when (some? (.-fullscreenEnabled js/document))
              (is (false? (.-fullscreenEnabled d))
                  "a feature that the page doesn't allow")))
         #(.remove f))))
