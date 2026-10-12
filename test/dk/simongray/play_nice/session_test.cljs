(ns dk.simongray.play-nice.session-test
  "The pure functions of the Media Session, and the calls without a
  session."
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.play-nice.session :as session]))

(deftest the-actions
  (is (= session/actions
         ;; the MediaSessionAction enum of §5, in the spec's order
         (set (map keyword ["play" "pause" "seekbackward" "seekforward"
                            "previoustrack" "nexttrack" "skipad" "stop"
                            "seekto" "togglemicrophone" "togglecamera"
                            "togglescreenshare" "hangup" "previousslide"
                            "nextslide" "enterpictureinpicture"
                            "voiceactivity"])))))

(deftest the-position-state
  (is (= {:duration 3600 :playback-rate 1.5 :position 754}
         (session/position-state 3600 1.5 754)))
  (is (nil? (session/position-state ##NaN 1.0 0))
      "until the duration is known")
  (is (nil? (session/position-state nil 1.0 0)))
  (is (nil? (session/position-state 0 1.0 0)))
  (is (= {:duration 3600 :playback-rate 1.0 :position 3600}
         (session/position-state 3600 0 4000))
      "a rate of 0 and a position past the end, which the browser refuses")
  (is (= 0 (:position (session/position-state 3600 1.0 ##NaN))))
  (is (= 0 (:position (session/position-state 3600 1.0 ##Inf))))
  (is (= 0 (:position (session/position-state 3600 1.0 -5))))
  (is (= 1.0 (:playback-rate (session/position-state 3600 ##NaN 0))))
  (is (= 1.0 (:playback-rate (session/position-state 3600 ##Inf 0))))
  (is (= -1 (:playback-rate (session/position-state 3600 -1 0))) "backwards")
  (is (= {:duration ##Inf :playback-rate 1.0 :position 754}
         (session/position-state ##Inf 1.0 754))
      "a live stream"))

(deftest a-media-element
  (is (nil? (session/element-position {:duration      ##NaN
                                        :current-time  0
                                        :playback-rate 1}))
      "before its metadata")
  (is (= {:duration 60 :playback-rate 2 :position 10}
         (session/element-position {:duration      60
                                    :current-time  10
                                    :playback-rate 2})))
  (is (= ##Inf (:duration (session/element-position {:duration      ##Inf
                                                     :current-time  5
                                                     :playback-rate 1})))
      "a live stream")
  (is (= {:playback-state :playing
          :position-state {:duration 60 :playback-rate 1 :position 10}}
         (session/element-state session/element-position
                                {:duration      60
                                 :current-time  10
                                 :playback-rate 1
                                 :paused        false})))
  (is (= {:playback-state :paused :position-state nil}
         (session/element-state (constantly nil) {:paused true}))
      "the position left to the browser"))

(deftest seeking
  (let [offsets {:seekbackward 15 :seekforward 30}
        seek    (fn [details current-time duration]
                  (session/seek-position details
                                         {:current-time current-time
                                          :duration     duration}
                                         offsets))]
    (is (= 1200 (seek {:action :seekto :seek-time 1200} 754 3600)))
    (is (= 3600 (seek {:action :seekto :seek-time 4000} 754 3600))
        "within the duration")
    (is (= 739 (seek {:action :seekbackward} 754 3600)))
    (is (= 744 (seek {:action :seekbackward :seek-offset 10} 754 3600))
        "the offset of the details first")
    (is (= 784 (seek {:action :seekforward} 754 3600)))
    (is (= 0 (seek {:action :seekbackward} 5 3600)) "not before the start")
    (is (= 3600 (seek {:action :seekforward} 3590 3600)) "nor past the end")
    (is (= 784 (seek {:action :seekforward} 754 ##Inf))
        "a live stream has no end")
    (is (= 784 (seek {:action :seekforward} 754 ##NaN))
        "nor a file whose duration isn't known")
    (is (nil? (seek {:action :seekforward} ##NaN 3600)) "no position")
    (is (nil? (seek {:action :seekto} 754 3600)) "no seek time")
    (is (nil? (seek {:action :play} 754 3600)))
    (is (nil? (session/seek-position {:action :seekforward}
                                     {:current-time 754 :duration 3600}
                                     {}))
        "no offset")))

(deftest the-metadata
  (is (= {:title        "Talk"
          :artwork      [{:src "cover.png"}]
          :chapter-info [{:title "Intro"}
                         {:title "Talk" :start-time 94 :artwork []}]}
         (session/metadata {:title        "Talk"
                            :artwork      [{:src "cover.png"}
                                           {:sizes "512x512"}]
                            :chapter-info [{:title "Intro"}
                                           {:title "Ads" :start-time -1}
                                           {:title "?" :start-time ##NaN}
                                           {:title      "Talk"
                                            :start-time 94
                                            :artwork    [{:type "png"}]}]}))
      "without images that have no src, or start times below 0 or NaN")
  (is (= {:title "Talk"} (session/metadata {:title "Talk"})))
  (is (= {:artwork [{:src "b.png"}] :chapter-info [{:artwork []}]}
         (session/metadata {:artwork      [{:src "a.png"} {:src "b.png"}]
                            :chapter-info [{:artwork [{:src "a.png"}]}]}
                           #(= "b.png" (:src %))))
      "the images that pass a predicate of your own"))

(deftest without-a-session
  (let [opts {:session nil}]
    (is (nil? (session/support opts)))
    (is (nil? (session/metadata! {:title "x"} opts)))
    (is (nil? (session/actions! {:play identity} opts)))
    (is (nil? (session/position! {:duration 60} opts)))
    (is (nil? (session/playback-state! :playing opts)))
    (is (nil? ((session/attach! #js {} opts)))
        "and a detach that does nothing")))
