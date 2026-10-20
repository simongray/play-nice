(ns dk.simongray.play-nice.page.tabs-test
  "One player at a time, where there are no Web Locks, and where the window
  fires no events, and the newest playback of the tabs and where it is."
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.play-nice.page.tabs :as tabs]))

(deftest without-locks
  (is (nil? ((tabs/attach! (js/EventTarget.) "app" {:window #js {}})))))

(deftest without-window-events
  (let [requests  (atom [])
        locks     #js {:request (fn [name _ _]
                                  (swap! requests conj name)
                                  (js/Promise. (fn [_ _])))}
        ^js audio (doto (js/EventTarget.) (unchecked-set "paused" true))
        detach!   (tabs/attach! audio "app" {:window #js {:navigator #js {:locks locks}}})]
    (.dispatchEvent audio (js/Event. "play"))
    (is (= ["app"] @requests)
        "the lock at play, where the window, like Node's global object, has Web Locks and no events")
    (is (nil? (detach!)))))

(deftest the-newest-playback
  (let [now 100000]
    (is (= "b" (:id (tabs/newest [{:id "a" :paused true :changed 5}
                                  {:id "b" :paused false :at now :changed 1}]
                                 now)))
        "one that plays")
    (is (= "a" (:id (tabs/newest [{:id "a" :paused true :changed 5}
                                  {:id "b" :paused true :changed 1}]
                                 now)))
        "else the one that changed last")
    (is (= {:id "b" :paused true :gone true}
           (select-keys (tabs/newest [{:id "b" :paused false :at (- now 20000) :changed 1}] now)
                        [:id :paused :gone]))
        "and a playing one whose tab has gone quiet counts as paused")
    (is (nil? (tabs/newest [] now)))))

(deftest where-a-playback-is
  (let [s {:current-time 100 :at 0 :paused false :status :ready :playback-rate 2 :duration 600}]
    (is (= 120 (tabs/position s 10000)) "moved on by the time since, at its rate")
    (is (= 600 (tabs/position s 1000000)) "no further than the duration")
    (is (= 100 (tabs/position (assoc s :paused true) 10000)) "still while it's paused")
    (is (= 100 (tabs/position (assoc s :status :buffering) 10000)) "or waits for data")))
