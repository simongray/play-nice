(ns dk.simongray.play-nice.media
  "The state of an audio or video element as data, and the calls that play
  it, seek, load a source and set its rate and volume, which apply! also
  takes as data.

  The functions take the element itself, so in a test any object with the
  element's attributes and methods stands in for it, e.g. an EventTarget in
  Node. The keys of the maps are the element's attributes in kebab-case,
  e.g. :current-time, and its numbered states are keywords, e.g.
  :have-metadata for HAVE_METADATA. The promises never reject, and each
  resolves to a map:

      (-> (play! audio)
          (.then prn))
      ;; prints {:playing true}, or e.g. {:refused :not-allowed}"
  (:require [clojure.string :as str]))

;; HTML Living Standard, §4.8.11.16, without timeupdate, which fires every
;; 15 to 250 ms while the element plays, and resize, for a video's size
(def events
  "The events of a media element that watch! follows by default."
  [:loadstart :progress :suspend :abort :error :emptied :stalled
   :loadedmetadata :loadeddata :canplay :canplaythrough :playing :waiting
   :seeking :seeked :ended :durationchange :play :pause :ratechange
   :volumechange])

;; §4.8.11.5 and §4.8.11.7
(def network-states
  "The network states of a media element, in the order of their values."
  [:empty :idle :loading :no-source])

(def ready-states
  "The ready states of a media element, in the order of their values."
  [:have-nothing :have-metadata :have-current-data :have-future-data
   :have-enough-data])

;; §4.8.11.1, MediaError
(def error-codes
  "The codes of a media element's error, by value."
  {1 :aborted 2 :network 3 :decode 4 :src-not-supported})

(defn error
  "The error of the media `element` as a map of its :code and :message, or
  nil while it has none, e.g. {:code :network}."
  [^js element]
  (when-let [^js e (.-error element)]
    (cond-> {:code (error-codes (.-code e))}
      (not-empty (.-message e)) (assoc :message (.-message e)))))

(defn ranges
  "The `time-ranges`, a TimeRanges object, as a vector of [start end] in
  seconds, e.g. [[0 812.4]]."
  [^js time-ranges]
  (mapv (fn [i] [(.start time-ranges i) (.end time-ranges i)])
        (range (if time-ranges (.-length time-ranges) 0))))

;; §4.16.3, the :buffering pseudo-class
(defn status
  "The status of a media element, from `state`, a map of its attributes:

  - :error, once it has failed
  - :ended, at the end
  - :buffering, while it waits for data to play, as HTML defines it for
    the :buffering pseudo-class
  - :idle, before it has the metadata
  - :ready, otherwise

  The status doesn't say whether the element plays, since :paused does,
  e.g. it plays while it's :ready and not :paused."
  [{:keys [error ended paused network-state ready-state] :as state}]
  (cond
    error :error
    ended :ended

    (and (not paused)
         (= :loading network-state)
         (#{:have-nothing :have-metadata :have-current-data} ready-state))
    :buffering

    (= :have-nothing ready-state) :idle
    :else                         :ready))

(defn state
  "The state of the media `element` as a map of its attributes and its
  :status, e.g.

      {:status        :buffering
       :paused        false
       :ended         false
       :seeking       false
       :ready-state   :have-current-data
       :network-state :loading
       :current-time  754.2
       :duration      3600
       :playback-rate 1.5
       :muted         false
       :volume        1
       :buffered      [[0 812.4]]
       :seekable      [[0 3600]]
       :played        [[0 754.2]]
       :error         nil}

  The :duration is nil until the metadata has loaded, rather than NaN,
  which isn't equal to itself, and ##Inf for a live stream. The :played
  ranges hold the seconds that were heard."
  [^js element]
  (let [duration (.-duration element)
        m        {:paused        (.-paused element)
                  :ended         (.-ended element)
                  :seeking       (.-seeking element)
                  :ready-state   (get ready-states (.-readyState element))
                  :network-state (get network-states (.-networkState element))
                  :current-time  (.-currentTime element)
                  :duration      (when-not (js/isNaN duration) duration)
                  :playback-rate (.-playbackRate element)
                  :muted         (.-muted element)
                  :volume        (.-volume element)
                  :buffered      (ranges (.-buffered element))
                  :seekable      (ranges (.-seekable element))
                  :played        (ranges (.-played element))
                  :error         (error element)}]
    (assoc m :status (status m))))

(defn watch!
  "Call `f` with the state of the media `element` now and whenever it
  changes, with the `opts` below, and return a function that stops it.

  - :events, the events of the element to read its state at, as keywords,
    by default events, which leaves out :timeupdate

  The call passes the map from state to `f` only when it differs from the
  last, e.g.

      (def stop!
        (watch! audio #(swap! app assoc :player %)))"
  ([element f]
   (watch! element f {}))
  ([^js element f {followed :events :or {followed events} :as opts}]
   (let [shown   (atom nil)
         follow! (fn [_]
                   (let [m (state element)]
                     (when (not= m @shown)
                       (reset! shown m)
                       (f m))))]
     (doseq [event followed]
       (.addEventListener element (name event) follow!))
     (follow! nil)
     (fn []
       (doseq [event followed]
         (.removeEventListener element (name event) follow!))
       nil))))

(defn ^:no-doc refusal
  "The refusal of the browser's exception `e`, its name as a keyword
  without Error, e.g. {:refused :not-allowed} for a NotAllowedError."
  [^js e]
  (let [s (str/replace (str (some-> e .-name)) #"Error$" "")]
    {:refused (-> (or (not-empty s) "error")
                  (str/replace #"\B[A-Z]" #(str "-" %))
                  str/lower-case
                  keyword)}))

;; §4.8.11.8: play() rejects with NotAllowedError, NotSupportedError, or
;; AbortError when a pause or a new load comes first
(defn- attempt!
  "A promise of the answer to playing the media `element` once."
  [^js element]
  (-> (js/Promise. (fn [resolve _] (resolve (.play element))))
      (.then (constantly {:playing true})
             (fn [e]
               (let [refused (refusal e)]
                 (if (= :abort (:refused refused))
                   {:superseded true}
                   refused))))))

(defn- play-muted!
  "A promise of the answer to playing the media `element` muted. Where the
  browser refuses that too, the call unmutes it again."
  [^js element]
  (set! (.-muted element) true)
  (.then (attempt! element)
         (fn [{:keys [playing] :as answer}]
           (if playing
             (assoc answer :muted true)
             (do (set! (.-muted element) false)
                 answer)))))

(defn play!
  "Play the media `element`, with the `opts` below, and return a promise of
  the browser's answer:

  - {:playing true} once it plays
  - {:refused :not-allowed} where the page may not play it, e.g. before
    the user has clicked
  - {:refused :not-supported} where its source won't play
  - {:superseded true} where a pause or a new source came first

  The options:

  - :muted-fallback, true to play it muted where the browser refuses its
    sound, which answers {:playing true :muted true}"
  ([element]
   (play! element {}))
  ([^js element {:keys [muted-fallback] :as opts}]
   (.then (attempt! element)
          (fn [answer]
            (if (and muted-fallback
                     (= :not-allowed (:refused answer))
                     (not (.-muted element)))
              (play-muted! element)
              answer)))))

;; §4.8.11.8 lets each engine choose its range: Chromium throws outside
;; 1/16 to 16, WebKit never throws, and Gecko throws only below 0
(defn rate
  "The playback rate nearest to `x` that every engine plays, from 1/16 to
  16, or nil where `x` isn't a finite number."
  [x]
  (when (js/Number.isFinite x)
    (max 0.0625 (min 16 x))))

(defn rate!
  "Set the playback rate of the media `element` to `x`, with the `opts`
  below, and return the rate as set, e.g. {:playback-rate 1.75}.

  The call keeps the rate within 1/16 and 16, which every engine plays,
  and returns `x` as :requested where it had to change it, e.g.
  {:playback-rate 16 :requested 20}. A rate that isn't a finite number
  leaves the rate as it was, and answers {:refused :not-finite}. The call
  sets the default playback rate too, since a new source resets the rate
  to it.

  - :preserves-pitch, false to let the pitch follow the rate, which every
    engine keeps by default"
  ([element x]
   (rate! element x {}))
  ([^js element x {:keys [preserves-pitch] :as opts}]
   (let [r (rate x)]
     (when r
       (set! (.-defaultPlaybackRate element) r)
       (set! (.-playbackRate element) r))
     (when (some? preserves-pitch)
       (set! (.-preservesPitch element) preserves-pitch))
     (cond-> {:playback-rate (or r (.-playbackRate element))}
       (nil? r)                (assoc :refused :not-finite)
       (and r (not= r x))      (assoc :requested x)
       (some? preserves-pitch) (assoc :preserves-pitch preserves-pitch)))))

;; §4.8.11.13 says only that the range of volume "need not be linear"
(def curves
  "The curves from the position of a volume slider, from 0 to 1, to a
  volume, and back: :linear, and :cubic, which sounds even along the
  slider, as e.g. PulseAudio's does."
  {:linear {:volume identity :level identity}
   :cubic  {:volume #(* % % %) :level js/Math.cbrt}})

(defn level
  "The position of a volume slider for the media `element`, from 0 to 1,
  with the `opts` below.

  - :curve, one of curves, :linear by default"
  ([element]
   (level element {}))
  ([^js element {:keys [curve] :or {curve :linear} :as opts}]
   ((get-in curves [curve :level]) (.-volume element))))

(defn volume!
  "Set the volume of the media `element` to the slider's `level`, from 0 to
  1, with the `opts` below, and return the volume as set, e.g. {:volume
  0.125} for a level of 0.5 on the :cubic curve.

  The call keeps the level within 0 and 1, and returns the `level` as
  :requested where it had to change it, e.g. {:volume 1 :requested 2}. A
  level that isn't a finite number leaves the volume as it was, and
  answers {:refused :not-finite}.

  An iPhone plays every element at the system's volume, whatever the
  page sets, which volume-locked! tells.

  - :curve, one of curves, :linear by default"
  ([element level]
   (volume! element level {}))
  ([^js element level {:keys [curve] :or {curve :linear} :as opts}]
   (if-not (js/Number.isFinite level)
     {:volume (.-volume element) :refused :not-finite}
     (let [kept (max 0 (min 1 level))
           v    ((get-in curves [curve :volume]) kept)]
       (set! (.-volume element) v)
       (cond-> {:volume v}
         (not= kept level) (assoc :requested level))))))

(defn muted!
  "Mute the media `element`, or unmute it where `muted?` is false, and
  return e.g. {:muted true}."
  [^js element muted?]
  (let [muted (boolean muted?)]
    (set! (.-muted element) muted)
    {:muted muted}))

;; §4.8.11.13: a user agent's volume locked, which the volume attribute
;; doesn't show, but WebKit on an iPhone fires no volumechange then
(defn volume-locked!
  "A promise of whether the browser locks the volume of media elements at
  the system's, as an iPhone does, in which case a page's volume slider
  does nothing, or of nil outside a browser. It never rejects.

  The call sets the volume of an element of its own, which it never plays,
  and waits half a second for the volumechange that a locked volume
  doesn't fire."
  []
  (js/Promise.
   (fn [resolve _]
     (if-not (exists? js/document)
       (resolve nil)
       (let [^js probe (js/document.createElement "audio")]
         (.addEventListener probe "volumechange" #(resolve false) #js {:once true})
         (js/setTimeout #(resolve true) 500)
         (set! (.-volume probe) 0.5))))))

;; §4.8.11.5, the load algorithm, and §4.8.11.6, the default playback
;; start position
(defn source!
  "Load `m`, a map of a :src and the :position and :playback-rate to start
  at, into the media `element`. Return `m` with the rate as set, e.g.
  {:src \"/272.mp3\" :position 754 :playback-rate 1.5}.

  A new source pauses the element without a pause event and resets its
  rate. So the call sets the src first, then the position, which the
  element seeks to once the metadata has loaded, and then the rate with
  rate!."
  [^js element {:keys [src position playback-rate] :as m}]
  (set! (.-src element) src)
  (when (js/Number.isFinite position)
    (set! (.-currentTime element) position))
  (cond-> m
    playback-rate (assoc :playback-rate
                         (:playback-rate (rate! element playback-rate)))))

(defn switch-source!
  "Load `src` into the media `element` at its position and rate, e.g. for
  another quality or format, and play it if it played. Return a promise of
  the answer of play!, or of {:paused true} where it was paused."
  [^js element src]
  (let [paused (.-paused element)]
    (source! element {:src           src
                      :position      (.-currentTime element)
                      :playback-rate (.-playbackRate element)})
    (if paused
      (js/Promise.resolve {:paused true})
      (play! element))))

(defn pause!
  "Pause the media `element`, and return {:paused true}."
  [^js element]
  (.pause element)
  {:paused true})

;; §4.8.11.7: setting currentTime to a number that isn't finite throws
(defn seek!
  "Move the media `element` to the `position` in seconds, and return the
  position as set, e.g. {:current-time 754}. A position that isn't a
  finite number, e.g. a duration of nil before the metadata, leaves the
  element where it was, and answers {:refused :not-finite}."
  [^js element position]
  (if (js/Number.isFinite position)
    (do (set! (.-currentTime element) position)
        {:current-time position})
    {:current-time (.-currentTime element) :refused :not-finite}))

(defn apply!
  "Apply the `effects` to the media `element` in order, and return a
  promise of a vector of their answers, which never rejects.

  The `effects` are one effect or a sequence of them. An effect is a
  vector of a keyword and the arguments that the function it names takes
  after the element, e.g. [:rate 1.5 {:preserves-pitch false}] for rate!:

  - [:play opts], play!
  - [:pause], pause!
  - [:seek position], seek!
  - [:rate x opts], rate!
  - [:volume level opts], volume!
  - [:muted muted?], muted!
  - [:source m], source!
  - [:switch-source src], switch-source!

  An effect of another kind does nothing, and answers {:refused
  :unknown-effect}. In re-frame, register apply! as an effect:

      (rf/reg-fx :media #(media/apply! audio %))

      {:fx [[:media [[:seek 754] [:play]]]]}"
  [^js element effects]
  (let [answer (fn [[kind & args :as effect]]
                 (js/Promise.
                  (fn [resolve _]
                    (resolve
                     (case kind
                       :play          (apply play! element args)
                       :pause         (apply pause! element args)
                       :seek          (apply seek! element args)
                       :rate          (apply rate! element args)
                       :volume        (apply volume! element args)
                       :muted         (apply muted! element args)
                       :source        (apply source! element args)
                       :switch-source (apply switch-source! element args)
                       {:refused :unknown-effect})))))]
    (-> (if (keyword? (first effects)) [effects] effects)
        (->> (mapv #(.catch (answer %) refusal)))
        into-array
        js/Promise.all
        (.then vec))))
