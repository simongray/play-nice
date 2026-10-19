(ns dk.simongray.play-nice.media-browser-test
  "The media element in a browser: muted videos that play a second of
  silence, and a file that doesn't play."
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.play-nice.media :as media]
            [dk.simongray.play-nice.test-support
             :refer [garbage muted-video next-event run silence]]))

(defn metadata
  "A promise that the media `element` has its metadata, or nil if it has."
  [^js element]
  (when (zero? (.-readyState element))
    (next-event element "loadedmetadata")))

(defn near?
  "Whether the seconds `x` and `y` differ by less than a hundredth, since
  WebKit seeks a little past, e.g. to 0.50005."
  [x y]
  (< (abs (- x y)) 0.01))

(deftest a-kept-level
  (let [key (str "play-nice-test-" (random-uuid))]
    (media/keep-level! key 0.4)
    (is (= 0.4 (media/kept-level key)) "the level that the browser keeps")
    (.setItem js/localStorage key "loud")
    (is (nil? (media/kept-level key)) "but no level that isn't a number")
    (.removeItem js/localStorage key)))

(deftest loading-a-source
  (let [^js v (muted-video)
        src   (silence)]
    (run #(is (= {:src src :position 0.5 :playback-rate 1.5}
                 (media/source! v {:src           src
                                   :position      0.5
                                   :playback-rate 1.5})))
         #(metadata v)
         #(is (near? 0.5 (.-currentTime v))
              "at the position, which the element seeks to after the metadata")
         #(is (= [1.5 1.5] [(.-playbackRate v) (.-defaultPlaybackRate v)])
              "at the rate, which the load would have reset")
         #(let [state (media/state v)]
            (is (= 1 (:duration state)))
            (is (= :ready (:status state)))
            (is (true? (:paused state)))))))

(deftest playing
  (let [^js v   (muted-video)
        ^js w   (muted-video)
        ^js bad (muted-video)]
    (media/source! v {:src (silence)})
    (run #(.then (media/play! v)
                 (fn [answer]
                   (is (= {:playing true} answer))
                   (is (false? (:paused (media/state v))))
                   (.pause v)))
         #(let [answer (do (media/source! w {:src (silence)})
                           (media/play! w))]
            (.pause w)
            (.then answer
                   (fn [a] (is (= {:superseded true} a) "a pause came first"))))
         #(do (media/source! bad {:src (garbage)})
              (.then (media/play! bad)
                     (fn [a] (is (= {:refused :not-supported} a)))))
         #(let [state (media/state bad)]
            (is (= :error (:status state)))
            (is (= :src-not-supported (:code (:error state))))))))

(deftest playing-with-sound
  (let [^js a (doto (js/document.createElement "video")
                (set! -playsInline true))]
    (media/source! a {:src (silence)})
    (run #(.then (media/play! a {:muted-fallback true})
                 (fn [answer]
                   (is (:playing answer) "with sound, or else muted")
                   (is (= (boolean (:muted answer)) (.-muted a)))
                   (.pause a))))))

(deftest the-rate
  (let [^js v (muted-video)]
    (is (= {:playback-rate 16 :requested 20} (media/rate! v 20))
        "where Chromium would throw")
    (is (= 16 (.-playbackRate v)))
    (is (= {:playback-rate 0.0625 :requested -1} (media/rate! v -1)))
    (is (= {:playback-rate 1.75 :preserves-pitch false}
           (media/rate! v 1.75 {:preserves-pitch false})))
    (is (= [1.75 false] [(.-playbackRate v) (.-preservesPitch v)]))))

(deftest the-volume
  (let [^js v (muted-video)]
    (is (= {:volume 0.125} (media/volume! v 0.5 {:curve :cubic})))
    (is (= 0.125 (.-volume v)) "which an iPhone shows too, though it ignores it")
    (is (< (abs (- 0.5 (media/level v {:curve :cubic}))) 1e-9))
    (run #(.then (media/volume-locked!)
                 (fn [locked]
                   (is (= (boolean (re-find #"iPhone" js/navigator.userAgent))
                          locked)
                       "only on an iPhone"))))))

(deftest switching-the-source
  (let [^js v (muted-video)]
    (media/source! v {:src (silence) :position 0.5 :playback-rate 0.5})
    (run #(metadata v)
         #(media/play! v)
         #(.then (media/switch-source! v (silence))
                 (fn [answer]
                   (is (= {:playing true} answer))
                   (is (<= 0.49 (.-currentTime v)) "where it was")
                   (is (= 0.5 (.-playbackRate v)))
                   (.pause v)))
         #(.then (media/switch-source! v (silence))
                 (fn [answer]
                   (is (= {:paused true} answer))
                   (is (true? (.-paused v))))))))

(deftest applying-effects
  (let [^js v (muted-video)
        src   (silence)]
    (run #(.then (media/apply! v [[:source {:src src}] [:rate 1.5] [:play]])
                 (fn [answers]
                   (is (= [{:src src} {:playback-rate 1.5} {:playing true}]
                          answers))
                   (is (false? (.-paused v)))))
         #(.then (media/apply! v [[:pause] [:seek 0.5] [:seek ##Inf]])
                 (fn [[paused seeked refused]]
                   (is (= [{:paused true} {:current-time 0.5}] [paused seeked]))
                   (is (= :not-finite (:refused refused))
                       "a position at which the element would throw")
                   (is (true? (.-paused v))))))))

(deftest watching-an-element
  (let [^js v  (muted-video)
        states (atom [])
        stop!  (atom nil)]
    (run #(reset! stop! (media/watch! v (partial swap! states conj)))
         #(is (= [:idle] (map :status @states)))
         ;; the ready state rather than the duration, which WebKit on Linux
         ;; reports as 0 for this file until a seek
         #(do (media/source! v {:src (silence)})
              (metadata v))
         #(is (some (comp #{:have-metadata :have-current-data
                            :have-future-data :have-enough-data}
                          :ready-state)
                    @states)
              "the metadata")
         #(media/play! v)
         #(is (false? (:paused (last @states))) "playing")
         #(let [paused (next-event v "pause")]
            (.pause v)
            paused)
         #(is (true? (:paused (last @states))))
         ;; the rate, since an iPhone locks the volume
         #(let [n       (count @states)
                changed (next-event v "ratechange")]
            (@stop!)
            (set! (.-playbackRate v) 2)
            (.then changed
                   (fn [_] (is (= n (count @states)) "until it stops")))))))
