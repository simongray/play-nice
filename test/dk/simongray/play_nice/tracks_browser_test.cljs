(ns dk.simongray.play-nice.tracks-browser-test
  "Text tracks in a browser: chapters followed through a second of silence,
  and captions to choose from."
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.play-nice.tracks :as tracks]
            [dk.simongray.play-nice.test-support
             :refer [muted-video next-event run silence]]))

(defn loaded
  "A promise that the last track element of the media `element` has
  loaded or failed to, at either of which add! adds its cues."
  [^js element]
  (let [^js track (.-lastElementChild element)]
    (js/Promise.race #js [(next-event track "load")
                          (next-event track "error")])))

(defn seek
  "Seek the media `element` to `t` seconds, and return a promise that its
  first text track's active cue has the `text`, or of a timeout after two
  seconds. WebKit changes the active cues of a seek later than seeked."
  [^js element t text]
  (let [^js track (aget (.-textTracks element) 0)]
    (js/Promise.
     (fn [resolve _]
       (let [check! (fn check! [_]
                      (when (= text (some-> (.-activeCues track)
                                            ^js (aget 0)
                                            .-text))
                        (.removeEventListener track "cuechange" check!)
                        (resolve true)))]
         (.addEventListener track "cuechange" check!)
         (js/setTimeout #(resolve false) 2000)
         (set! (.-currentTime element) t))))))

;; seeks rather than playback, which WebKit on Linux didn't finish, after a
;; play and a pause, since iOS doesn't seek an element before it has played
(deftest following-the-chapters
  (let [^js v   (muted-video)
        states  (atom [])
        remove! (atom nil)
        stop!   (atom nil)
        chapter (fn [] (-> @states last first :active-cues first :text))]
    (set! (.-src v) (silence))
    (run #(do (reset! stop! (tracks/watch! v (partial swap! states conj)))
              (reset! remove!
                      (tracks/add! v {:kind  :chapters
                                      :label "Chapters"
                                      :cues  (tracks/chapter-cues
                                              [{:title "A" :start-time 0}
                                               {:title "B" :start-time 0.5}]
                                              1)}))
              (js/Promise.all #js [(loaded v)
                                   (next-event v "loadedmetadata")]))
         #(is (= 2 (.-length (.-cues ^js (aget (.-textTracks v) 0)))))
         #(.play v)
         #(.pause v)
         #(seek v 0.75 "B")
         #(is (= "B" (chapter)) "cuechange at a chapter, from a hidden track")
         #(seek v 0.25 "A")
         #(is (= "A" (chapter)))
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
