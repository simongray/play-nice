(ns dk.simongray.play-nice.schema-test
  "The schemas, against what the pure functions return, and in
  ClojureScript against the library's own keywords."
  (:require [clojure.test :refer [deftest is]]
            [malli.core :as m]
            [dk.simongray.play-nice.media.fragment :as fragment]
            [dk.simongray.play-nice.schema :as schema]
            #?@(:cljs [[dk.simongray.play-nice.media :as media]
                       [dk.simongray.play-nice.session :as session]])))

(def schemas
  "Every schema of the namespace."
  [schema/ranges schema/media-error schema/media-state schema/play-answer
   schema/source schema/media-effect schema/image schema/metadata
   schema/position-state schema/action-details schema/caption-style
   schema/interval schema/fullscreen-state schema/picture-in-picture-state
   schema/remote-playback-state])

(deftest every-schema-reads
  (doseq [s schemas]
    (is (m/schema s) "since m/schema throws for one that Malli can't read")))

(deftest pure-results
  (is (m/validate schema/interval (fragment/time "a.ogv#t=10,20")))
  (is (m/validate schema/interval (fragment/time "a.ogv#t=10")))
  (is (m/validate schema/caption-style
                  {:font-scale 1.5 :color :yellow :edge :drop-shadow}))
  (is (m/validate schema/caption-style {:color "#fc0" :background-opacity 0}))
  (is (not (m/validate schema/caption-style {:edge :glow})))
  (is (m/validate schema/metadata
                  {:title        "Talk"
                   :artwork      [{:src "cover.png" :sizes "512x512"}]
                   :chapter-info [{:title "Intro" :start-time 0}]}))
  (is (not (m/validate schema/metadata {:artwork [{:sizes "512x512"}]}))
      "an image without a src")
  (is (m/validate schema/action-details {:action :seekto :seek-time 754}))
  (is (not (m/validate schema/action-details {:action :rewind}))))

(deftest effects
  (doseq [e [[:play] [:play {:muted-fallback true}] [:pause] [:seek 754]
             [:rate 1.5] [:rate 1.5 {:preserves-pitch false}]
             [:volume 0.5 {:curve :cubic}] [:muted true]
             [:source {:src "/272.mp3" :position 754}]
             [:switch-source "/272-1080.mp4"]]]
    (is (m/validate schema/media-effect e) (pr-str e)))
  (doseq [e [[:seek "soon"] [:pause 1] [:skip 30] [:source {:position 1}]]]
    (is (not (m/validate schema/media-effect e)) (pr-str e))))

(defn entry
  "The schema of the key `k` in the map schema `s`."
  [s k]
  (some (fn [[key & more]] (when (= k key) (last more))) (rest s)))

#?(:cljs
   (deftest the-keywords-of-the-library
     (is (= media/ready-states (rest (entry schema/media-state :ready-state))))
     (is (= media/network-states (rest (entry schema/media-state :network-state))))
     (is (= (set (vals media/error-codes))
            (set (rest (entry schema/media-error :code)))))
     (is (= session/actions (set (rest (entry schema/action-details :action)))))))
