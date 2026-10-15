(ns dk.simongray.play-nice.media.tracks-test
  "The text tracks' pure functions, and the others on stand-ins for an
  element and its tracks."
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.play-nice.media.tracks :as tracks]))

(defn track
  "A stand-in for a TextTrack: an EventTarget of the `kind`, `language`
  and `mode`, and the active `cues`, a vector of [start end text]."
  ([kind language mode]
   (track kind language mode []))
  ([kind language mode cues]
   (let [^js t (js/EventTarget.)]
     (set! (.-kind t) kind)
     (set! (.-label t) "")
     (set! (.-language t) language)
     (set! (.-mode t) mode)
     (set! (.-activeCues t)
           (clj->js (map (fn [[start end text]]
                           {:startTime start :endTime end :text text :id ""})
                         cues)))
     t)))

(defn element
  "A stand-in for a media element whose textTracks list the `tracks`."
  [& tracks]
  #js {:textTracks (into-array tracks)})

(deftest the-cues
  (is (= [{:start-time 0 :end-time 95 :text "Intro"}
          {:start-time 95 :end-time 1830 :text "News" :id "news"}]
         (tracks/cues #js [#js {:startTime 0 :endTime 95 :text "Intro" :id ""}
                           #js {:startTime 95 :endTime 1830 :text "News"
                                :id        "news"}])))
  (is (= [] (tracks/cues nil)) "a disabled track has none"))

(deftest a-track
  (is (= {:kind        :captions
          :label       ""
          :language    "da"
          :mode        :showing
          :active-cues [{:start-time 12 :end-time 15.5 :text "Hej"}]}
         (tracks/text-track (track "captions" "da" "showing"
                                   [[12 15.5 "Hej"]]))))
  (is (= [:chapters :subtitles]
         (map :kind (tracks/text-tracks (element (track "chapters" "" "hidden")
                                                 (track "subtitles" "en"
                                                        "disabled"))))))
  (is (= [] (tracks/text-tracks #js {})) "no tracks"))

(deftest chapters-as-cues
  (is (= [{:start-time 0 :end-time 95 :text "Intro"}
          {:start-time 95 :end-time 1830 :text "News"}]
         (tracks/chapter-cues [{:title "News" :start-time 95}
                               {:title "Intro" :start-time 0}]
                              1830))
      "in the order of their start")
  (is (= [{:start-time 0 :end-time 1830 :text ""}]
         (tracks/chapter-cues [{}] 1830))
      "with the defaults of the session's chapters")
  (is (= [] (tracks/chapter-cues [] 1830))))

(deftest showing-a-track
  (let [chapters (track "chapters" "" "hidden")
        en       (track "captions" "en-GB" "showing")
        da       (track "subtitles" "da" "disabled")
        el       (element chapters en da)
        modes    #(mapv (fn [^js t] (.-mode t)) [chapters en da])]
    (is (= :subtitles (:kind (tracks/show! el {:kinds     [:captions :subtitles]
                                               :languages ["da" "en"]}))))
    (is (= ["hidden" "disabled" "showing"] (modes))
        "the language first, and only one of the kinds")
    (is (= "en-GB" (:language (tracks/show! el {:kinds     [:captions :subtitles]
                                                :languages ["fr" "en"]})))
        "a regional variant")
    (is (= ["hidden" "showing" "disabled"] (modes)))
    (is (nil? (tracks/show! el {:kinds [:captions :subtitles]})))
    (is (= ["hidden" "disabled" "disabled"] (modes))
        "none, without languages")))

(defn track-list
  "A stand-in for a TextTrackList: an EventTarget that's array-like."
  []
  (doto (js/EventTarget.)
    (unchecked-set "length" 0)))

(defn add-track!
  "Add the stand-in `track` to the stand-in `list`, as a browser would."
  [^js list track]
  (unchecked-set list (.-length list) track)
  (set! (.-length list) (inc (.-length list)))
  (.dispatchEvent list (js/Event. "addtrack")))

(deftest watching-the-tracks
  (let [states  (atom [])
        ^js ts  (track-list)
        ^js ch  (track "chapters" "" "hidden")
        stop!   (tracks/watch! #js {:textTracks ts} #(swap! states conj %))
        actives #(map (comp (partial map :text) :active-cues first) @states)]
    (is (= [[]] @states) "the tracks at once")
    (add-track! ts ch)
    (set! (.-activeCues ch) #js [#js {:startTime 0 :endTime 95 :text "Intro"}])
    (.dispatchEvent ch (js/Event. "cuechange"))
    (is (= [[] [] ["Intro"]] (actives)) "a new track, and its cue")
    (.dispatchEvent ch (js/Event. "cuechange"))
    (is (= 3 (count @states)) "only when they change")
    (is (nil? (stop!)))
    (set! (.-activeCues ch) #js [])
    (.dispatchEvent ch (js/Event. "cuechange"))
    (is (= 3 (count @states)) "until it stops")
    (is (nil? ((tracks/watch! #js {} identity))) "and nothing without tracks")
    (is (nil? ((tracks/watch! #js {:textTracks #js []} identity)))
        "or with tracks that fire no events, as in jsdom")))

(deftest adding-without-a-document
  (is (nil? ((tracks/add! #js {} {:kind :chapters :cues []})))))
