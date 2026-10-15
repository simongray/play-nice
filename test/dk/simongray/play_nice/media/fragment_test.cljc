(ns dk.simongray.play-nice.media.fragment-test
  "Media fragments and page links, with the examples of Media Fragments URI
  1.0. The times are doubles, as on the JVM."
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.play-nice.media.fragment :as fragment]))

(deftest normal-play-time
  (is (= 73.5 (fragment/npt "73.5")))
  (is (= 73.0 (fragment/npt "73.")))
  (is (= 120.0 (fragment/npt "0:02:00")))
  (is (= 121.5 (fragment/npt "0:02:01.5")))
  (is (= 125.0 (fragment/npt "02:05")) "minutes and seconds")
  (is (= 3723.0 (fragment/npt "1:02:03")))
  (is (nil? (fragment/npt "1:30")) "minutes take two digits")
  (is (nil? (fragment/npt "0:61:00")) "and stay below 60")
  (is (nil? (fragment/npt "")))
  (is (nil? (fragment/npt "abc"))))

(deftest intervals
  ;; the examples of §4.2.1
  (is (= {:start 10.0 :end 20.0} (fragment/interval "npt:10,20")))
  (is (= {:start 0.0 :end 121.5} (fragment/interval "npt:,121.5")))
  (is (= {:start 120.0 :end 121.5} (fragment/interval "0:02:00,121.5")))
  (is (= {:start 120.0 :end 121.5} (fragment/interval "npt:120,0:02:01.5")))
  (is (= {:start 10.0} (fragment/interval "10")) "to the end")
  (is (nil? (fragment/interval "10,5")) "an end before the start")
  (is (nil? (fragment/interval "10,")))
  (is (nil? (fragment/interval "")))
  (is (nil? (fragment/interval "smpte:00:00:10")) "a format of the advanced profile"))

(deftest fragments
  (is (= {:start 73.0 :end 133.0}
         (fragment/time "https://cdn.example/272.mp3#t=npt:73,133")))
  (is (= {:start 10.0 :end 20.0}
         (fragment/time "https://example.com/example.ogv#track=audio&t=10,20")))
  (is (= {:start 10.0} (fragment/time "a.ogv#t=2&t=10")) "the last valid one")
  (is (= {:start 2.0} (fragment/time "a.ogv#t=2&t=x")) "the last valid one")
  (is (= {:start 10.0} (fragment/time "a.ogv#%74=%6ept%3A%310"))
      "percent-encoded, as in §5.1.1")
  (is (= {:start 1.0} (fragment/time "a.ogv#id=%xy&t=1")) "past a bad encoding")
  (is (nil? (fragment/time "https://cdn.example/272.mp3")))
  (is (nil? (fragment/time "https://cdn.example/272.mp3?t=73"))
      "a query isn't a fragment"))

(deftest page-links
  (is (= 73.0 (fragment/link-time "https://example.com/272?t=73")))
  (is (= 73.0 (fragment/link-time "https://example.com/272?t=73s")))
  (is (= 73.0 (fragment/link-time "https://example.com/272?a=b&t=1m13s#x")))
  (is (= 3723.0 (fragment/link-time "https://example.com/272?t=1h2m3s")))
  (is (= 73.0 (fragment/link-time "https://example.com/272?t=01:13")))
  (is (nil? (fragment/link-time "https://example.com/272?t=")))
  (is (nil? (fragment/link-time "https://example.com/272?t=soon")))
  (is (nil? (fragment/link-time "https://example.com/272#t=73"))))
