(ns dk.simongray.play-nice.capabilities-browser-test
  "What a browser says it can play, for the files that apps meet."
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.play-nice.capabilities :as capabilities]
            [dk.simongray.play-nice.test-support :refer [run]]))

(def mp3
  {:type :file :audio {:content-type "audio/mpeg"}})

(def aac
  "AAC in MP4, as podcast feeds give it, without codecs."
  {:type :file :audio {:content-type "audio/mp4"}})

(def nonsense
  {:type :file :audio {:content-type "audio/x-nonsense"}})

(def h264
  "A video without its width, height, bitrate and frame rate."
  {:type :file :video {:content-type "video/mp4; codecs=\"avc1.42E01E\""}})

(deftest the-answers-of-can-play-type
  (is (= :probably (capabilities/can-play-type "audio/mpeg")))
  (is (= :maybe (capabilities/can-play-type "audio/mp4")))
  (is (nil? (capabilities/can-play-type "audio/x-nonsense"))))

(deftest the-files-that-apps-meet
  (run #(.then (capabilities/decoding-infos! [mp3 aac nonsense h264 mp3])
               (fn [infos]
                 (is (= #{mp3 aac nonsense h264} (set (keys infos)))
                     "each configuration once")
                 (is (true? (:supported (infos mp3))))
                 (is (capabilities/playable? (infos aac))
                     "AAC without codecs, which Chromium alone would refuse")
                 (is (not (capabilities/playable? (infos nonsense))))
                 (is (= :probably (:can-play-type (infos h264)))
                     "a video that Media Capabilities rejects")))))

(deftest the-first-file-that-plays
  (let [files [nonsense aac mp3]]
    (run #(.then (capabilities/decoding-infos! files)
                 (fn [infos]
                   (is (= aac (first (filter (comp capabilities/playable? infos)
                                             files)))))))))
