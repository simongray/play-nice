(ns dk.simongray.play-nice.page.tabs-test
  "One player at a time, where there are no Web Locks, and where the window
  fires no events."
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
