(ns dk.simongray.play-nice.media-test
  "The media element's pure functions, and the others on a stand-in for an
  element."
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.play-nice.util.keys :as keys]
            [dk.simongray.play-nice.media :as media]
            [dk.simongray.play-nice.test-support :refer [run]]))

(defn time-ranges
  "A stand-in for a TimeRanges object of the [start end] `pairs`."
  [pairs]
  #js {:length (count pairs)
       :start  #(first (nth pairs %))
       :end    #(second (nth pairs %))})

(def attributes
  "The attributes of a stand-in element before it has a source."
  {:paused                true
   :ended                 false
   :seeking               false
   :ready-state           0
   :network-state         0
   :current-time          0
   :duration              ##NaN
   :playback-rate         1
   :default-playback-rate 1
   :muted                 false
   :volume                1
   :buffered              (time-ranges [])
   :seekable              (time-ranges [])
   :played                (time-ranges [])})

(defn element
  "A stand-in for a media element: an EventTarget with the attributes of
  `m` over those above, e.g. {:paused false}."
  [m]
  (let [^js el (js/EventTarget.)]
    (doseq [[k v] (merge attributes m)]
      (unchecked-set el (keys/keyword->member k) v))
    el))

(defn refused
  "A promise rejected with the DOMException named `s`."
  [s]
  (js/Promise.reject (js/DOMException. "refused" s)))

(defn player
  "A stand-in for a media element whose play returns what `f` returns for
  the element."
  [f]
  (let [^js el (element {})]
    (set! (.-play el) #(f el))
    el))

(defn muted-only
  "What a browser's play returns for the stand-in `element` where it only
  plays muted media."
  [^js element]
  (if (.-muted element)
    (js/Promise.resolve)
    (refused "NotAllowedError")))

(deftest the-status
  (is (= :idle (media/status {:paused true :ready-state :have-nothing})))
  (is (= :buffering (media/status {:paused        false
                                   :network-state :loading
                                   :ready-state   :have-nothing}))
      "after play, before the metadata")
  (is (= :buffering (media/status {:paused        false
                                   :network-state :loading
                                   :ready-state   :have-current-data})))
  (is (= :ready (media/status {:paused        true
                               :network-state :loading
                               :ready-state   :have-current-data}))
      "only an element that plays buffers")
  (is (= :ready (media/status {:paused        false
                               :network-state :idle
                               :ready-state   :have-current-data}))
      "nor one that has all its data")
  (is (= :ready (media/status {:paused        false
                               :network-state :loading
                               :ready-state   :have-future-data})))
  (is (= :ended (media/status {:paused      true
                               :ended       true
                               :ready-state :have-enough-data})))
  (is (= :error (media/status {:paused      false
                               :ready-state :have-enough-data
                               :error       {:code :network}}))
      "after a fatal error, which leaves it unpaused"))

(deftest time-ranges-as-vectors
  (is (= [[0 12.5] [30 60]] (media/ranges (time-ranges [[0 12.5] [30 60]]))))
  (is (= [] (media/ranges (time-ranges []))))
  (is (= [] (media/ranges nil))))

(deftest the-state
  (is (= {:status        :idle
          :paused        true
          :ended         false
          :seeking       false
          :ready-state   :have-nothing
          :network-state :empty
          :current-time  0
          :duration      nil
          :playback-rate 1
          :muted         false
          :volume        1
          :buffered      []
          :seekable      []
          :played        []
          :error         nil}
         (media/state (element {})))
      "without a source, and without NaN")
  (let [state (media/state (element {:paused        false
                                     :ready-state   2
                                     :network-state 2
                                     :current-time  754.2
                                     :duration      3600
                                     :buffered      (time-ranges [[0 812.4]])
                                     :played        (time-ranges [[0 754.2]])}))]
    (is (= :buffering (:status state)))
    (is (= :have-current-data (:ready-state state)))
    (is (= [[0 812.4]] (:buffered state)))
    (is (= [[0 754.2]] (:played state))))
  (is (= ##Inf (:duration (media/state (element {:duration ##Inf}))))
      "a live stream")
  (is (= {:code :network}
         (media/error (element {:error #js {:code 2 :message ""}}))))
  (is (= {:code :decode :message "PIPELINE_ERROR_DECODE"}
         (media/error (element {:error #js {:code    3
                                            :message "PIPELINE_ERROR_DECODE"}}))))
  (is (= :error (:status (media/state (element {:paused false
                                                :error  #js {:code 2}})))))
  (is (nil? (media/error (element {})))))

(deftest refusals
  (let [refusal (comp :refused media/refusal #(js/DOMException. "" %))]
    (is (= :not-allowed (refusal "NotAllowedError")))
    (is (= :not-supported (refusal "NotSupportedError")))
    (is (= :security (refusal "SecurityError"))))
  (is (= {:refused :type} (media/refusal (js/TypeError. "x"))))
  (is (= {:refused :error} (media/refusal (js/Error. "x"))))
  (is (= {:refused :error} (media/refusal "x"))))

(deftest the-rate
  (is (= 1.75 (media/rate 1.75)))
  (is (= 16 (media/rate 20)))
  (is (= 0.0625 (media/rate 0.01)))
  (is (= 0.0625 (media/rate 0)))
  (is (= 0.0625 (media/rate -1)) "no engine but WebKit plays backwards")
  (is (nil? (media/rate ##NaN)))
  (is (nil? (media/rate ##Inf)))
  (is (nil? (media/rate nil))))

(deftest setting-the-rate
  (let [^js el (element {})]
    (is (= {:playback-rate 1.75} (media/rate! el 1.75)))
    (is (= [1.75 1.75] [(.-playbackRate el) (.-defaultPlaybackRate el)])
        "and the default, which a new source resets the rate to")
    (is (= {:playback-rate 16 :requested 20} (media/rate! el 20)))
    (is (= 16 (.-playbackRate el)))
    (is (= {:playback-rate 16 :refused :not-finite} (media/rate! el "fast"))
        "the rate as it was")
    (is (= {:playback-rate 16 :refused :not-finite} (media/rate! el nil)))
    (is (= {:playback-rate 2 :preserves-pitch false}
           (media/rate! el 2 {:preserves-pitch false})))
    (is (false? (.-preservesPitch el)))))

(deftest setting-the-volume
  (let [^js el (element {})]
    (is (= {:volume 0.5} (media/volume! el 0.5)))
    (is (= {:volume 0.125} (media/volume! el 0.5 {:curve :cubic})))
    ;; Math.cbrt in WebKit on Linux is an ulp off
    (is (< (abs (- 0.5 (media/level el {:curve :cubic}))) 1e-9)
        "the slider's level back")
    (is (= 0.125 (media/level el)))
    (is (= {:volume 1 :requested 2} (media/volume! el 2)) "within 0 and 1")
    (is (= {:volume 0 :requested -1} (media/volume! el -1)))
    (is (= {:volume 0 :refused :not-finite} (media/volume! el "loud"))
        "the volume as it was")))

(deftest pausing-and-muting
  (let [^js el  (element {})
        paused (atom 0)]
    (set! (.-pause el) #(swap! paused inc))
    (is (= {:paused true} (media/pause! el)))
    (is (= 1 @paused))
    (is (= {:muted true} (media/muted! el true)))
    (is (true? (.-muted el)))
    (is (= {:muted false} (media/muted! el nil)))
    (is (false? (.-muted el)))))

(defn loader
  "A stand-in for a media element that adds the name of each attribute set
  to the atom `calls`, and resets the rate to the default at a new src, as
  the load algorithm does."
  [calls]
  (let [^js el (element {})
        prop!  (fn [member]
                 (let [v (atom (unchecked-get el member))]
                   (js/Object.defineProperty
                    el member
                    #js {:get #(deref v)
                         :set (fn [x]
                                (swap! calls conj member)
                                (reset! v x)
                                (when (= "src" member)
                                  (set! (.-playbackRate el)
                                        (.-defaultPlaybackRate el))))})))]
    (run! prop! ["src" "currentTime" "playbackRate" "defaultPlaybackRate"])
    el))

(deftest loading-a-source
  (let [calls  (atom [])
        ^js el (loader calls)]
    (is (= {:src "/272.mp3" :position 754 :playback-rate 16}
           (media/source! el {:src "/272.mp3" :position 754 :playback-rate 20})))
    (is (= ["src" "playbackRate" "currentTime" "defaultPlaybackRate"
            "playbackRate"]
           @calls)
        "the src first, whose load resets the rate, and then the rest")
    (is (= [754 16] [(.-currentTime el) (.-playbackRate el)]))
    (reset! calls [])
    (is (= {:src "/273.mp3"} (media/source! el {:src "/273.mp3"})))
    (is (= ["src" "playbackRate"] @calls))
    (is (= 16 (.-playbackRate el)) "the rate kept by its default")))

(deftest playing
  (let [answer! (fn [promise] (media/play! (player (constantly promise))))]
    (run #(.then (answer! (js/Promise.resolve))
                 (fn [a] (is (= {:playing true} a))))
         #(.then (answer! (refused "NotAllowedError"))
                 (fn [a] (is (= {:refused :not-allowed} a))))
         #(.then (answer! (refused "NotSupportedError"))
                 (fn [a] (is (= {:refused :not-supported} a))))
         #(.then (answer! (refused "AbortError"))
                 (fn [a] (is (= {:superseded true} a))))
         #(.then (answer! nil)
                 (fn [a] (is (= {:playing true} a) "a play without a promise")))
         #(.then (media/play! (player (fn [_] (throw (js/Error. "no")))))
                 (fn [a] (is (= {:refused :error} a) "or one that throws"))))))

(deftest playing-muted
  (let [^js a (player muted-only)
        ^js b (player (fn [_] (refused "NotAllowedError")))
        ^js c (player muted-only)]
    (run #(.then (media/play! a {:muted-fallback true})
                 (fn [answer]
                   (is (= {:playing true :muted true} answer))
                   (is (true? (.-muted a)))))
         #(.then (media/play! b {:muted-fallback true})
                 (fn [answer]
                   (is (= {:refused :not-allowed} answer))
                   (is (false? (.-muted b)) "unmuted, as it was")))
         #(.then (media/play! c)
                 (fn [answer]
                   (is (= {:refused :not-allowed} answer) "only when asked"))))))

(deftest switching-the-source
  (let [plays  (atom 0)
        ^js a  (element {:paused false :current-time 754 :playback-rate 1.5})
        ^js b  (element {:paused true :current-time 12})]
    (set! (.-play a) #(do (swap! plays inc) (js/Promise.resolve)))
    (run #(.then (media/switch-source! a "/272-1080.mp4")
                 (fn [answer]
                   (is (= {:playing true} answer))
                   (is (= 1 @plays))
                   (is (= ["/272-1080.mp4" 754 1.5 1.5]
                          [(.-src a) (.-currentTime a) (.-playbackRate a)
                           (.-defaultPlaybackRate a)]))))
         #(.then (media/switch-source! b "/273.mp3")
                 (fn [answer]
                   (is (= {:paused true} answer))
                   (is (= 12 (.-currentTime b))))))))

(deftest seeking
  (let [^js el (element {:current-time 12})]
    (is (= {:current-time 754} (media/seek! el 754)))
    (is (= 754 (.-currentTime el)))
    (is (= {:current-time 754 :refused :not-finite} (media/seek! el ##Inf))
        "where the element would throw")
    (is (= {:current-time 754 :refused :not-finite} (media/seek! el nil))
        "e.g. a duration before the metadata")
    (let [fast (atom nil)]
      (set! (.-fastSeek el) #(reset! fast %))
      (is (= {:current-time 900} (media/seek! el 900 {:fast true})))
      (is (= [900 754] [@fast (.-currentTime el)]) "with fastSeek, where it has it"))))

(deftest applying-effects
  (let [^js el (player (constantly (js/Promise.resolve)))
        paused (atom 0)]
    (set! (.-pause el) #(swap! paused inc))
    (run #(.then (media/apply! el [[:rate 1.5] [:seek 754] [:muted true]
                                   [:volume 0.5 {:curve :cubic}] [:play]])
                 (fn [answers]
                   (is (= [{:playback-rate 1.5} {:current-time 754} {:muted true}
                           {:volume 0.125} {:playing true}]
                          answers))
                   (is (= [1.5 754 true 0.125]
                          [(.-playbackRate el) (.-currentTime el) (.-muted el)
                           (.-volume el)]))))
         #(.then (media/apply! el [:pause])
                 (fn [answers]
                   (is (= [{:paused true}] answers) "one effect alone")
                   (is (= 1 @paused))))
         #(.then (media/apply! el [[:skip 30] [:source {:src "/272.mp3"}]])
                 (fn [answers]
                   (is (= [{:refused :unknown-effect} {:src "/272.mp3"}] answers)
                       "and the rest, after one it doesn't know")))
         #(.then (media/apply! (element {}) [:play])
                 (fn [answers]
                   (is (= [{:refused :type}] answers)
                       "a refusal, where the element throws")))
         #(.then (media/apply! el nil)
                 (fn [answers] (is (= [] answers)))))))

(deftest watching-an-element
  (let [states (atom [])
        ^js el (element {})
        stop!  (media/watch! el #(swap! states conj %))]
    (is (= [:idle] (map :status @states)) "the state at once")
    (.dispatchEvent el (js/Event. "progress"))
    (is (= 1 (count @states)) "and only when it changes")
    (set! (.-paused el) false)
    (set! (.-networkState el) 2)
    (.dispatchEvent el (js/Event. "play"))
    (is (= [:idle :buffering] (map :status @states)))
    (set! (.-currentTime el) 5)
    (.dispatchEvent el (js/Event. "timeupdate"))
    (is (= 2 (count @states)) "without timeupdate")
    (is (nil? (stop!)))
    (set! (.-paused el) true)
    (.dispatchEvent el (js/Event. "pause"))
    (is (= 2 (count @states)) "until it stops")
    (media/watch! el #(swap! states conj %) {:events [:timeupdate]})
    (set! (.-currentTime el) 6)
    (.dispatchEvent el (js/Event. "timeupdate"))
    (is (= [5 6] (map :current-time (drop 2 @states)))
        "or with the events you choose")))
