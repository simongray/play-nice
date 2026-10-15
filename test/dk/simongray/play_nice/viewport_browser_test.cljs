(ns dk.simongray.play-nice.viewport-browser-test
  "Autoplay in view in a browser, with a muted video at the top of a page
  that scrolls it out of view and back."
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.play-nice.viewport :as viewport]
            [dk.simongray.play-nice.test-support
             :refer [muted-video run silence]]))

(defn until
  "A promise that the atom `states` has a last state of which `pred` is
  true, or a rejection after 8 seconds, named by the `label`."
  [states pred label]
  (js/Promise.
   (fn [resolve reject]
     (let [k (gensym)]
       (if (pred (last @states))
         (resolve true)
         (do (add-watch states k (fn [_ _ _ s]
                                   (when (pred (last s))
                                     (remove-watch states k)
                                     (resolve true))))
             (js/setTimeout #(do (remove-watch states k)
                                 (reject (str "not " label " in 8 s")))
                            8000)))))))

(deftest playing-in-view
  (let [^js page   (js/document.createElement "div")
        ^js v      (muted-video)
        ^js spacer (js/document.createElement "div")
        states     (atom [])
        scroll!    (fn [y] (js/window.scrollTo 0 y))
        stop!      (atom nil)]
    (set! (.-src v) (silence))
    (set! (.-loop v) true)
    (set! (.. v -style -height) "120px")
    (set! (.. spacer -style -height) "4000px")
    (.appendChild page v)
    (.appendChild page spacer)
    (.prepend js/document.body page)
    (scroll! 0)
    (run #(reset! stop! (viewport/autoplay! v (partial swap! states conj)
                                            {:reduced-motion false}))
         #(until states (every-pred :visible :playing) "playing in view")
         #(scroll! 3000)
         #(until states (every-pred (complement :visible) (complement :playing))
                 "paused out of view")
         #(scroll! 0)
         #(until states (every-pred :visible :playing) "playing again")
         #(.pause v)
         #(until states (comp #{:visitor} :held) "held by the visitor")
         #(scroll! 3000)
         #(scroll! 0)
         #(js/Promise. (fn [resolve _] (js/setTimeout resolve 500)))
         #(is (= {:visible true :playing false :held :visitor} (last @states))
              "and left to the visitor")
         #(do (@stop!)
              (.remove page)))))
