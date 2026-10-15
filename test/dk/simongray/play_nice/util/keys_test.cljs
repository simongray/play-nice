(ns dk.simongray.play-nice.util.keys-test
  "Converting the specs' member names both ways, and maps to JavaScript
  objects and back."
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.play-nice.util.keys :as keys]))

(def members
  "Every member name of the dictionaries of Media Session and Media
  Capabilities, as their IDL has them."
  ["title" "artist" "album" "artwork" "chapterInfo" "src" "sizes" "type"
   "startTime" "duration" "playbackRate" "position" "action" "seekOffset"
   "seekTime" "fastSeek" "isActivating" "enterPictureInPictureReason"
   "keySystemConfiguration" "video" "audio" "contentType" "width" "height"
   "bitrate" "framerate" "hasAlphaChannel" "hdrMetadataType" "colorGamut"
   "transferFunction" "scalabilityMode" "spatialScalability" "channels"
   "samplerate" "spatialRendering" "keySystem" "initDataType"
   "distinctiveIdentifier" "persistentState" "sessionTypes" "robustness"
   "encryptionScheme" "supported" "smooth" "powerEfficient"
   "keySystemAccess" "configuration"])

(deftest member-names
  (is (= :seek-time (keys/member->keyword "seekTime")))
  (is (= :enter-picture-in-picture-reason
         (keys/member->keyword "enterPictureInPictureReason")))
  (is (= :title (keys/member->keyword "title")))
  (is (= "chapterInfo" (keys/keyword->member :chapter-info)))
  (is (= members (map (comp keys/keyword->member keys/member->keyword)
                      members))
      "every name both ways"))

(deftest maps-to-javascript-and-back
  (let [m {:title        "Episode 3"
           :artwork      [{:src "cover.png" :sizes "512x512"}]
           :chapter-info [{:title "Intro" :start-time 0}]}]
    (is (= m (keys/->clj (keys/->js m)))))
  (let [^js o (keys/->js {:action :seekto :seek-time 1 :fast-seek nil})]
    (is (= ["action" "seekTime"] (vec (js-keys o)))
        "no nil values, since WebIDL reads null as \"null\"")
    (is (= "seekto" (unchecked-get o "action")) "a keyword is its name"))
  (is (= {:action :seekto :seek-time 1 :title "play"}
         (keys/->clj #js {"action" "seekto" "seekTime" 1 "title" "play"}
                     #{:action}))
      "only the enums become keywords"))
