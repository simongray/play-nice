(ns dk.simongray.play-nice.captions-browser-test
  "Caption styles in a page, for one video at a time."
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.play-nice.captions :as captions]
            [dk.simongray.play-nice.test-support :refer [muted-video]]))

(defn sheets
  "The style elements of the page that hold a rule of ::cue."
  []
  (filter #(.includes (.-textContent ^js %) "::cue")
          (js/Array.from (js/document.querySelectorAll "style"))))

(deftest styling-a-video
  (let [^js v   (muted-video)
        ^js w   (muted-video)
        before  (count (sheets))
        remove! (captions/style! v {:color :yellow :font-scale 1.5})]
    (is (= (inc before) (count (sheets))))
    (let [^js sheet (.-sheet ^js (last (sheets)))]
      (is (= 1 (.-length (.-cssRules sheet)))
          "a rule that the browser parses, with ::cue")
      (is (.matches v (str "video[data-play-nice-captions=\""
                           (.getAttribute v "data-play-nice-captions")
                           "\"]")))
      (is (not (.hasAttribute w "data-play-nice-captions"))
          "and no other video"))
    (is (nil? (remove!)))
    (is (= before (count (sheets))))
    (is (not (.hasAttribute v "data-play-nice-captions")))))

(deftest styling-again
  (let [^js v  (muted-video)
        first! (captions/style! v {:color :yellow})
        later! (captions/style! v {:color :cyan})
        id     (.getAttribute v "data-play-nice-captions")]
    (first!)
    (is (= id (.getAttribute v "data-play-nice-captions"))
        "the later style stays")
    (later!)
    (is (not (.hasAttribute v "data-play-nice-captions")))))
