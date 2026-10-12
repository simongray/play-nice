(ns dk.simongray.play-nice.capabilities-test
  "What the browser can play: the answers that count as playable, and an
  answer rather than a rejection for any configuration."
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.play-nice.capabilities :as capabilities]
            [dk.simongray.play-nice.test-support :refer [run]]))

(deftest playable
  (is (capabilities/playable? {:supported       true
                               :smooth          false
                               :power-efficient false}))
  (is (capabilities/playable? {:supported false :can-play-type :maybe})
      "where canPlayType says maybe")
  (is (capabilities/playable? {:can-play-type :probably})
      "without Media Capabilities")
  (is (not (capabilities/playable? {:supported false :can-play-type nil})))
  (is (not (capabilities/playable? nil))))

(deftest an-invalid-configuration
  (run #(.then (capabilities/decoding-info! {:type :file})
               (fn [info]
                 (is (= {:can-play-type nil} info)
                     "neither audio nor video, which is invalid")))
       #(.then (capabilities/decoding-info! nil)
               (fn [info]
                 (is (= {:can-play-type nil} info)
                     "none at all, e.g. for a file without a type")))))
