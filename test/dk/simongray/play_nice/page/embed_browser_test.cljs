(ns dk.simongray.play-nice.page.embed-browser-test
  "The allow attribute of an iframe in a browser, whose document reports
  what the attribute allows."
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.play-nice.page.embed :as embed]
            [dk.simongray.play-nice.test-support :refer [next-event run silence]]))

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

(defn knows?
  "Whether the document `d` knows the `feature`, which only Chrome says."
  [^js d feature]
  (some-> (.-featurePolicy d) (.features) (.includes feature)))

(defn frame-video
  "A muted video element in the frame `f` that loops a second of silence,
  since Chrome plays only a muted video without a click."
  [^js f]
  (let [^js d (.-contentDocument f)
        ^js v (.createElement d "video")]
    (set! (.-muted v) true)
    (set! (.-loop v) true)
    (set! (.-src v) (silence))
    (.append (.-body d) v)
    v))

(deftest a-hidden-frame-without-playback
  (let [^js f (frame {:media-playback-while-not-visible :none})
        video (atom nil)
        knows #(knows? (.-contentDocument f) "media-playback-while-not-visible")]
    (run #(next-event f "load")
         #(when (knows)
            (reset! video (frame-video f))
            (.play ^js @video))
         #(when (knows)
            (let [paused (next-event @video "pause")]
              (set! (.. f -style -display) "none")
              paused))
         #(when (knows)
            (is (.-paused ^js @video) "paused once the frame is hidden"))
         #(.remove f))))
