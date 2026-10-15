(ns dk.simongray.play-nice.media.tracks
  "The text tracks of a media element as data: captions and subtitles to
  choose and show, and chapters to follow in every browser.

  A track is a map of the members of TextTrack in kebab-case, with the
  values of its enums as keywords, e.g.

      {:kind        :chapters
       :label       \"Chapters\"
       :language    \"en\"
       :mode        :hidden
       :active-cues [{:start-time 95 :end-time 1830 :text \"News\"}]}

  Only Chromium accepts chapters in the Media Session's metadata, but a
  hidden chapters track fires cuechange in every engine. So a chapters
  track from add! and chapter-cues, followed with watch!, says which
  chapter plays."
  (:require [clojure.string :as str]))

;; HTML Living Standard, §4.8.11.11.5, TextTrackCue, and WebVTT, §7.1, VTTCue
(defn cues
  "The `cue-list`, a TextTrackCueList, as a vector of maps of each cue's
  :start-time, :end-time and :text, and its :id where it has one."
  [^js cue-list]
  (mapv (fn [^js cue]
          (cond-> {:start-time (.-startTime cue)
                   :end-time   (.-endTime cue)
                   :text       (.-text cue)}
            (not-empty (.-id cue)) (assoc :id (.-id cue))))
        (some-> cue-list js/Array.from)))

;; §4.8.11.11.5, TextTrack
(defn text-track
  "The `track`, a TextTrack, as a map of its :kind, :label, :language,
  :mode and :active-cues. A disabled track has no active cues."
  [^js track]
  {:kind        (keyword (.-kind track))
   :label       (.-label track)
   :language    (.-language track)
   :mode        (keyword (.-mode track))
   :active-cues (cues (.-activeCues track))})

(defn text-tracks
  "The text tracks of the media `element` as a vector of maps from
  text-track."
  [^js element]
  (mapv text-track (some-> (.-textTracks element) js/Array.from)))

(defn watch!
  "Call `f` with the text tracks of the media `element` now and whenever
  they change, and return a function that stops it.

  They change when a track is added or removed, when its mode changes, and
  when its active cues change, e.g. at the start of a chapter. The call
  passes the vector from text-tracks to `f` only when it differs from the
  last, e.g.

      (def stop!
        (watch! video #(swap! app assoc :tracks %)))

  Where the element's tracks fire no events, e.g. in jsdom, the call does
  nothing."
  [^js element f]
  (if-let [^js tracks (let [^js ts (.-textTracks element)]
                        (when (fn? (some-> ts .-addEventListener))
                          ts))]
    (let [events   ["addtrack" "removetrack" "change"]
          followed (js/Set.)
          shown    (atom nil)
          follow!  (fn follow! [_]
                     ;; cuechange fires at each track, not at the list
                     (doseq [^js track (js/Array.from tracks)]
                       (when-not (.has followed track)
                         (.add followed track)
                         (.addEventListener track "cuechange" follow!)))
                     (let [v (text-tracks element)]
                       (when (not= v @shown)
                         (reset! shown v)
                         (f v))))]
      (doseq [event events]
        (.addEventListener tracks event follow!))
      (follow! nil)
      (fn []
        (doseq [event events]
          (.removeEventListener tracks event follow!))
        (.forEach followed #(.removeEventListener ^js % "cuechange" follow!))
        nil))
    (constantly nil)))

;; Media Session, §7, ChapterInformationInit: a :start-time of 0 and a
;; :title of "" by default
(defn chapter-cues
  "The cues of a chapters track for the `chapters` of a media of `duration`
  seconds, e.g. the :chapter-info of the session's metadata. Each chapter
  lasts until the next one starts, and the last until the end, so give
  the duration once it's known, since add! leaves out a cue that has no
  finite end:

      (chapter-cues [{:title \"Intro\" :start-time 0}
                     {:title \"News\" :start-time 95}]
                    1830)
      ;; => [{:start-time 0 :end-time 95 :text \"Intro\"}
      ;;     {:start-time 95 :end-time 1830 :text \"News\"}]"
  [chapters duration]
  (let [chapters (sort-by #(or (:start-time %) 0) chapters)
        starts   (map #(or (:start-time %) 0) chapters)]
    (mapv (fn [{:keys [title]} start end]
            {:start-time start :end-time end :text (or title "")})
          chapters
          starts
          (concat (rest starts) [duration]))))

(defn- vtt-cue
  "A VTTCue of the `cue`, a map of its :start-time, :end-time, :text and
  :id, or nil where the browser refuses it, e.g. for a start time that
  isn't finite."
  [{:keys [start-time end-time text id] :as cue}]
  (try
    (let [^js c (js/VTTCue. start-time end-time (str text))]
      (when id
        (set! (.-id c) id))
      c)
    (catch :default _ nil)))

(defn add!
  "Add a text track to the media `element` from `track`, a map of its
  :kind, :label, :language, :mode and :cues, and return a function that
  removes it, e.g.

      (def remove!
        (add! audio {:kind :chapters
                     :cues (chapter-cues chapter-info 1830)}))

  The :kind is :subtitles and the :mode is :hidden by default, which
  follows the cues without showing them. The cues are maps of their
  :start-time, :end-time and :text, and the call leaves out those that the
  browser refuses, e.g. for a start time that isn't finite.

  The track comes from a track element, since a track from addTextTrack
  can never be removed. The cues follow once the element has loaded, since
  Chrome and WebKit drop the cues added before."
  [^js element {:keys [kind label language mode cues]
                :or   {kind :subtitles mode :hidden}
                :as   track}]
  (if-let [^js document (.-ownerDocument element)]
    (let [^js el    (.createElement document "track")
          add-cues! (fn [_]
                      (let [^js t (.-track el)]
                        (doseq [^js cue (keep vtt-cue cues)]
                          (.addCue t cue))))]
      (set! (.-kind el) (name kind))
      (when label
        (set! (.-label el) label))
      (when language
        (set! (.-srclang el) language))
      ;; a file without cues to load, since a track without one fails, and
      ;; the cues also at an error, e.g. where the page forbids data: URLs
      (set! (.-src el) "data:text/vtt,WEBVTT")
      (.addEventListener el "load" add-cues! #js {:once true})
      (.addEventListener el "error" add-cues! #js {:once true})
      (.appendChild element el)
      (set! (.-mode (.-track el)) (name mode))
      (fn []
        (.remove el)
        nil))
    (constantly nil)))

(defn- language?
  "Whether the `tag` of a track, e.g. \"en-GB\", has the `language`, e.g.
  \"en\" or \"en-GB\", ignoring case."
  [language tag]
  (let [l (str/lower-case language)
        t (str/lower-case (str tag))]
    (or (= l t) (str/starts-with? t (str l "-")))))

(defn show!
  "Show the first text track of the media `element` that `prefs` choose,
  and turn off its other tracks of those kinds. Return the track as shown,
  or nil where none matches, which turns them all off.

  The `prefs` are a map of the :kinds and the :languages that you accept,
  each in the order that you prefer, e.g.

      (show! video {:kinds     [:captions :subtitles]
                    :languages [\"da\" \"en\"]})

  The language counts first, so Danish subtitles beat English captions,
  and a language includes its regional variants, e.g. \"en\" includes
  \"en-GB\". Without :languages, no track matches, which turns the kinds
  off."
  [^js element {:keys [kinds languages] :as prefs}]
  (let [kind     #(keyword (.-kind ^js %))
        of-kinds (filter (comp (set kinds) kind)
                         (some-> (.-textTracks element) js/Array.from))
        chosen   (first (for [language languages
                              k        kinds
                              ^js t    of-kinds
                              :when    (and (= k (kind t))
                                            (language? language (.-language t)))]
                          t))]
    (doseq [^js t of-kinds
            :when (not (identical? t chosen))]
      (set! (.-mode t) "disabled"))
    (when-let [^js t chosen]
      (set! (.-mode t) "showing")
      (text-track t))))
