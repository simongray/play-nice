(ns dk.simongray.play-nice.fragment-test
  "Media fragments, page links and clips, with the examples of Media
  Fragments URI 1.0."
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.play-nice.fragment :as fragment]
            [dk.simongray.play-nice.session :as session]))

(deftest normal-play-time
  (is (= 73.5 (fragment/npt "73.5")))
  (is (= 73 (fragment/npt "73.")))
  (is (= 120 (fragment/npt "0:02:00")))
  (is (= 121.5 (fragment/npt "0:02:01.5")))
  (is (= 125 (fragment/npt "02:05")) "minutes and seconds")
  (is (= 3723 (fragment/npt "1:02:03")))
  (is (nil? (fragment/npt "1:30")) "minutes take two digits")
  (is (nil? (fragment/npt "0:61:00")) "and stay below 60")
  (is (nil? (fragment/npt "")))
  (is (nil? (fragment/npt "abc"))))

(deftest intervals
  ;; the examples of §4.2.1
  (is (= {:start 10 :end 20} (fragment/interval "npt:10,20")))
  (is (= {:start 0 :end 121.5} (fragment/interval "npt:,121.5")))
  (is (= {:start 120 :end 121.5} (fragment/interval "0:02:00,121.5")))
  (is (= {:start 120 :end 121.5} (fragment/interval "npt:120,0:02:01.5")))
  (is (= {:start 10} (fragment/interval "10")) "to the end")
  (is (nil? (fragment/interval "10,5")) "an end before the start")
  (is (nil? (fragment/interval "10,")))
  (is (nil? (fragment/interval "")))
  (is (nil? (fragment/interval "smpte:00:00:10")) "a format of the advanced profile"))

(deftest fragments
  (is (= {:start 73 :end 133}
         (fragment/time "https://cdn.example/272.mp3#t=npt:73,133")))
  (is (= {:start 10 :end 20}
         (fragment/time "https://example.com/example.ogv#track=audio&t=10,20")))
  (is (= {:start 10} (fragment/time "a.ogv#t=2&t=10")) "the last valid one")
  (is (= {:start 2} (fragment/time "a.ogv#t=2&t=x")) "the last valid one")
  (is (= {:start 10} (fragment/time "a.ogv#%74=%6ept%3A%310"))
      "percent-encoded, as in §5.1.1")
  (is (= {:start 1} (fragment/time "a.ogv#id=%xy&t=1")) "past a bad encoding")
  (is (nil? (fragment/time "https://cdn.example/272.mp3")))
  (is (nil? (fragment/time "https://cdn.example/272.mp3?t=73"))
      "a query isn't a fragment"))

(deftest page-links
  (is (= 73 (fragment/link-time "https://example.com/272?t=73")))
  (is (= 73 (fragment/link-time "https://example.com/272?t=73s")))
  (is (= 73 (fragment/link-time "https://example.com/272?a=b&t=1m13s#x")))
  (is (= 3723 (fragment/link-time "https://example.com/272?t=1h2m3s")))
  (is (= 73 (fragment/link-time "https://example.com/272?t=01:13")))
  (is (nil? (fragment/link-time "https://example.com/272?t=")))
  (is (nil? (fragment/link-time "https://example.com/272?t=soon")))
  (is (nil? (fragment/link-time "https://example.com/272#t=73"))))

(deftest clips
  (let [element {:current-time 103 :duration 3600 :playback-rate 1.5}]
    (is (= {:duration 60 :playback-rate 1.5 :position 30}
           (session/clip-position {:start 73 :end 133} element)))
    (is (= {:duration 3527 :playback-rate 1.5 :position 30}
           (session/clip-position {:start 73} element))
        "to the end of the file")
    (is (= 0 (:position (session/clip-position {:start 200 :end 260} element)))
        "before the clip")
    (is (nil? (session/clip-position {:start 73} (assoc element :duration ##NaN)))
        "while the duration is unknown")))
