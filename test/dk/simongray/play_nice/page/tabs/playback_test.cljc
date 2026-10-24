(ns dk.simongray.play-nice.page.tabs.playback-test
  "The newest playback of the tabs, and where it is."
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.play-nice.page.tabs.playback :as playback]))

(deftest the-newest-playback
  (let [now 100000]
    (is (= "b" (:id (playback/newest [{:id "a" :paused true :changed 5}
                                      {:id "b" :paused false :at now :changed 1}]
                                     now)))
        "one that plays")
    (is (= "a" (:id (playback/newest [{:id "a" :paused true :changed 5}
                                      {:id "b" :paused true :changed 1}]
                                     now)))
        "else the one that changed last")
    (is (= {:id "b" :paused true :gone true}
           (select-keys (playback/newest [{:id "b" :paused false :at (- now 20000) :changed 1}] now)
                        [:id :paused :gone]))
        "and a playing one whose tab has gone quiet counts as paused")
    (is (nil? (playback/newest [] now)))))

;; == rather than =, since the JVM moves a position on in doubles
(deftest where-a-playback-is
  (let [s {:current-time 100 :at 0 :paused false :status :ready :playback-rate 2 :duration 600}]
    (is (== 120 (playback/position s 10000)) "moved on by the time since, at its rate")
    (is (== 600 (playback/position s 1000000)) "no further than the duration")
    (is (== 100 (playback/position (assoc s :paused true) 10000)) "still while it's paused")
    (is (== 100 (playback/position (assoc s :status :buffering) 10000)) "or waits for data")
    (is (== 2100 (playback/position (assoc s :duration ##Inf) 1000000)) "or with no end, e.g. live")))
