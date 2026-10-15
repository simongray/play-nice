(ns dk.simongray.play-nice.tracks-browser-test
  "Text tracks in a browser: chapters followed through a second of silence,
  and captions to choose from."
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.play-nice.tracks :as tracks]
            [dk.simongray.play-nice.test-support
             :refer [muted-video next-event run silence]]))

(defn loaded
  "A promise that the last track element of the media `element` has
  loaded."
  [^js element]
  (next-event (.-lastElementChild element) "load"))

(deftest following-the-chapters
  (let [^js v   (muted-video)
        states  (atom [])
        remove! (atom nil)
        stop!   (atom nil)
        texts   (fn [] (keep (comp first (partial map :text) :active-cues first)
                             @states))]
    (set! (.-src v) (silence))
    (run #(do (reset! stop! (tracks/watch! v (partial swap! states conj)))
              (reset! remove!
                      (tracks/add! v {:kind  :chapters
                                      :label "Chapters"
                                      :cues  (tracks/chapter-cues
                                              [{:title "A" :start-time 0}
                                               {:title "B" :start-time 0.5}]
                                              1)}))
              (loaded v))
         #(is (= 2 (.-length (.-cues ^js (aget (.-textTracks v) 0)))))
         #(let [ended (next-event v "ended")]
            (.play v)
            ended)
         #(is (= ["A" "B"] (distinct (texts)))
              "cuechange at each chapter, from a hidden track")
         #(is (= {:kind :chapters :label "Chapters" :mode :hidden}
                 (select-keys (first (last @states)) [:kind :label :mode])))
         #(let [removed (next-event (.-textTracks v) "removetrack")]
            (@remove!)
            removed)
         #(is (= [] (last @states)) "removed, unlike a track from addTextTrack")
         #(@stop!))))

(deftest refused-cues
  (let [^js v (muted-video)]
    (tracks/add! v {:cues [{:start-time 0 :end-time 1 :text "kept"}
                           {:start-time ##NaN :end-time 1 :text "refused"}]})
    (run #(loaded v)
         #(let [^js t (aget (.-textTracks v) 0)]
            (is (= ["kept"] (map :text (tracks/cues (.-cues t)))))
            (is (= :subtitles (keyword (.-kind t))) "subtitles by default")))))

(deftest choosing-captions
  (let [^js v (muted-video)]
    (tracks/add! v {:kind :captions :language "en" :label "English"})
    (tracks/add! v {:kind :subtitles :language "da-DK" :label "Dansk"})
    (is (= "Dansk" (:label (tracks/show! v {:kinds     [:captions :subtitles]
                                            :languages ["da" "en"]}))))
    (is (= [:disabled :showing] (map :mode (tracks/text-tracks v))))
    (is (nil? (tracks/show! v {:kinds [:captions :subtitles]})))
    (is (= [:disabled :disabled] (map :mode (tracks/text-tracks v))))))
