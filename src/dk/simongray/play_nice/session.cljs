(ns dk.simongray.play-nice.session
  "The page's Media Session, which puts what plays on the lock screen and
  in the notifications, and lets the headphone buttons control it.

  The functions that touch the browser do nothing where it has no Media
  Session, e.g. in Node or the Android WebView. Each takes these opts:

  - :session, the MediaSession to set instead of the page's, e.g. a
    stand-in in a test, or nil for none

  The keys of the maps are the members of the spec's dictionaries in
  kebab-case, and the values of its enums are keywords, e.g. {:action
  :seekto :seek-time 754.2}. A player whose buttons just play, pause and
  seek its media element needs three calls:

      (def detach!
        (attach! audio))

      (metadata! {:title \"Blue in Green\" :artist \"Miles Davis\"})

      (actions! (element-actions audio {:seekbackward 10
                                        :seekforward  10}))"
  (:require [dk.simongray.play-nice.keys :as keys]))

;; W3C Media Session, Working Draft of 5 June 2026, §5, MediaSessionAction
(def actions
  "Every action of the Media Session."
  #{:play :pause :seekbackward :seekforward :previoustrack :nexttrack :skipad
    :stop :seekto :togglemicrophone :togglecamera :togglescreenshare :hangup
    :previousslide :nextslide :enterpictureinpicture :voiceactivity})

(def element-events
  "The events of a media element that attach! listens to: those that change
  its duration or rate, make its position jump, start or stop it, or give
  it a new source."
  ["loadedmetadata" "durationchange" "play" "playing" "pause" "seeked"
   "ratechange" "ended" "emptied"])

(defn ^:no-doc finite?
  "Whether `x` is a number that's neither infinite nor NaN."
  [x]
  (js/Number.isFinite x))

(defn ^:no-doc within
  "The position `x`, kept between 0 and the `duration`. A duration that
  isn't finite sets no upper bound."
  [duration x]
  (cond-> (max 0 x)
    (finite? duration) (min duration)))

;; §9, MediaPositionState, and the steps of setPositionState in §5
(defn position-state
  "The position state of a player `position` seconds into a `duration` at
  `rate`, or nil while the duration is unknown.

  The position stays within the duration, and a rate of 0, or one that
  isn't finite, counts as 1, since setPositionState throws for either. An
  infinite duration means a live stream."
  [duration rate position]
  (when (and (number? duration) (pos? duration))
    {:duration      duration
     :playback-rate (if (and (finite? rate) (not (zero? rate))) rate 1)
     :position      (within duration (if (finite? position) position 0))}))

(defn element-position
  "The position state of a media element, from `element`, a map of its
  :duration, :current-time and :playback-rate, or nil while the duration
  is unknown."
  [{:keys [duration current-time playback-rate] :as element}]
  (position-state duration playback-rate current-time))

(defn element-state
  "The :playback-state and :position-state that the session shows for
  `element`, a map of a media element's attributes. The position state
  comes from `position-fn`, e.g. element-position."
  [position-fn {:keys [paused] :as element}]
  {:playback-state (if paused :paused :playing)
   :position-state (position-fn element)})

;; §10: seekTime for seekto, and a seekOffset that may be missing for
;; seekbackward and seekforward
(defn seek-position
  "Where the seek action of `details` goes on a media element, from
  `element`, a map of its :current-time and :duration, and the `offsets`
  of seekbackward and seekforward in seconds, e.g. {:seekbackward 15
  :seekforward 30}.

  An offset in the details goes first. The position stays between 0 and
  the duration, and it's nil for another action, or for a seek without a
  time or an offset."
  [{:keys [action seek-offset seek-time] :as details}
   {:keys [current-time duration] :as element}
   offsets]
  (let [offset (if (finite? seek-offset) seek-offset (get offsets action))
        target (case action
                 :seekto       seek-time
                 :seekbackward (some->> offset (- current-time))
                 :seekforward  (some->> offset (+ current-time))
                 nil)]
    (when (finite? target)
      (within duration target))))

(defn- chapter?
  "Whether the `chapter` has a start time that Chromium accepts: none,
  which means 0, or a finite number of 0 or more."
  [{t :start-time :as chapter}]
  (or (nil? t) (and (finite? t) (>= t 0))))

;; §6 to §8: MediaMetadataInit, ChapterInformationInit and MediaImage
(defn metadata
  "The metadata `m`, a map of the keys of MediaMetadataInit, without what a
  browser would throw for. That's the chapters whose :start-time is
  negative, infinite or NaN, and the images that fail `image-pred`, by
  default those without a :src."
  ([m]
   (metadata m :src))
  ([m image-pred]
   (let [only     (partial filterv image-pred)
         images   #(cond-> % (:artwork %) (update :artwork only))
         chapters (comp (filter chapter?) (map images))]
     (cond-> (images m)
       (:chapter-info m) (update :chapter-info (partial into [] chapters))))))

(defn ^:no-doc succeeds?
  "Whether calling `f` returns rather than throws."
  [f]
  (try
    (f)
    true
    (catch :default _ false)))

(defonce ^{:no-doc true
           :doc    "A WeakMap from each session to an atom of what the
                   library set on it:

                   - :actions, those it gave a handler
                   - :metadata, the last metadata it set, and :object,
                     its MediaMetadata
                   - :detach, the function that ends the attachment
                   - :support, the answer of support"}
  states
  (js/WeakMap.))

(defn ^:no-doc state
  "The atom of what the library set on the `session`."
  [^js session]
  (or (.get states session)
      (let [a (atom {})]
        (.set states session a)
        a)))

(defn media-session
  "The MediaSession that the `opts` name as :session, or else the page's,
  or nil where the browser has none."
  [opts]
  (let [^js navigator (.-navigator js/globalThis)]
    (:session opts (some-> navigator .-mediaSession))))

(defn- details
  "The details of an action as a map, from the browser's object `o`."
  [o]
  ;; §10: the action and the reason for Picture-in-Picture are enums
  (keys/->clj o #{:action :enter-picture-in-picture-reason}))

(defn ^:no-doc set-handler!
  "Set the handler of the `action` on the `session` to `f`, a function of
  the action's details as a map, or clear it for nil. Return whether the
  browser accepts the action."
  [^js session action f]
  (succeeds? #(.setActionHandler session
                                 (name action)
                                 (some-> f (comp details)))))

(defn support
  "What the Media Session supports, with the `opts` above, or nil where the
  browser has none:

  - :actions, the set of actions that it accepts
  - :position-state?, whether it accepts a position state
  - :chapter-info?, whether its metadata can have chapters

  No browser lists its actions, but each throws for an action it lacks. So
  the first call for a session clears each action that the library hasn't
  set, and keeps those that don't throw. Call it before setting any
  handler yourself, outside the library."
  ([]
   (support {}))
  ([opts]
   (when-let [^js session (media-session opts)]
     (let [st     (state session)
           ours   (:actions @st #{})
           takes? #(set-handler! session % nil)
           probe  (fn []
                    {:actions         (into ours
                                            (filter takes?)
                                            (remove ours actions))
                     :position-state? (fn? (.-setPositionState session))
                     :chapter-info?   (and (exists? js/MediaMetadata)
                                           (js-in "chapterInfo"
                                                  (.-prototype
                                                   js/MediaMetadata)))})]
       (or (:support @st)
           (:support (swap! st assoc :support (probe))))))))

(defn actions!
  "Set the `handlers` of the Media Session, a map of action to a function
  of the details, with the `opts` above. Return the set of actions that
  the browser refused, or nil where it has no Media Session.

  A nil function clears its action, and so does leaving out an action that
  an earlier call set. The details are a map of the keys of
  MediaSessionActionDetails, e.g. {:action :seekto :seek-time 754.2}. Each
  engine refuses actions of its own, e.g. Firefox refuses those of video
  calls."
  ([handlers]
   (actions! handlers {}))
  ([handlers opts]
   (when-let [^js session (media-session opts)]
     (let [st     (state session)
           ;; clear what an earlier call set, unless the handlers set it again
           wanted (merge (zipmap (:actions @st) (repeat nil)) handlers)
           taken  (into #{}
                        (keep (fn [[action f]]
                                (when (set-handler! session action f)
                                  action)))
                        wanted)]
       (swap! st assoc :actions (into #{} (filter wanted) taken))
       (into #{} (comp (map key) (remove taken)) handlers)))))

(defn- parses?
  "Whether the src of the `image` parses as a URL, as the MediaMetadata
  constructor parses it against the page."
  [image]
  (succeeds? #(js/MediaMetadata. (keys/->js {:artwork [image]}))))

(defn metadata!
  "Show the metadata `m`, a map of the keys of MediaMetadataInit, in the
  Media Session, or clear it for nil, with the `opts` above. Return the
  metadata as set, or nil where the browser has no Media Session.

  Before setting it, the call leaves out what the browser would throw for,
  i.e. what the function metadata leaves out and the images whose URL
  doesn't parse. A browser without chapters ignores them. Each change makes
  a new MediaMetadata, the one way that works alike in every engine, but
  setting the same metadata twice in a row does nothing."
  ([m]
   (metadata! m {}))
  ([m opts]
   (when-let [^js session (and (exists? js/MediaMetadata)
                               (media-session opts))]
     (let [st     (state session)
           m      (some-> m (metadata parses?))
           shown? (and (= m (:metadata @st))
                       (identical? (.-metadata session) (:object @st)))]
       (when-not shown?
         (let [object (some-> m keys/->js js/MediaMetadata.)]
           (set! (.-metadata session) object)
           (swap! st assoc :metadata m :object object)))
       m))))

(defn position!
  "Show the position `state`, a map of the keys of MediaPositionState, in
  the Media Session, or clear it for nil, with the `opts` above. Return the
  state as set, or nil.

  The call first corrects the state with position-state. If the browser
  still refuses it, e.g. an infinite duration in Safari and Firefox, the
  call clears the state, so that the browser follows the media element
  itself."
  ([state]
   (position! state {}))
  ([{:keys [duration playback-rate position] :as state} opts]
   (when-let [^js session (media-session opts)]
     (when (fn? (.-setPositionState session))
       (let [state (position-state duration playback-rate position)]
         (if (and state
                  (succeeds? #(.setPositionState session (keys/->js state))))
           state
           ;; without an argument, since Safari throws for {}
           (do (.setPositionState session)
               nil)))))))

(defn playback-state!
  "Declare the playback `state` of the Media Session, :none, :paused or
  :playing, with the `opts` above."
  ([state]
   (playback-state! state {}))
  ([state opts]
   (when-let [^js session (media-session opts)]
     (set! (.-playbackState session) (name state))
     state)))

(defn- element-attributes
  "The attributes of the media `element` that a session follows."
  [^js element]
  {:duration      (.-duration element)
   :current-time  (.-currentTime element)
   :playback-rate (.-playbackRate element)
   :paused        (.-paused element)})

(defn element-actions
  "Handlers that play, pause, stop and seek the media `element` itself. The
  `offsets` give the seconds of seekbackward and seekforward, e.g.
  {:seekbackward 15 :seekforward 30}, and a skip without one is left out,
  since the spec has no default.

  Merge your own handlers over them before passing them to actions!. Keep
  seekto among them: Safari drops its own buttons once a page sets a
  handler, so without seekto its lock screen has no scrubber."
  ([element]
   (element-actions element {}))
  ([^js element offsets]
   (let [go!   (fn [to fast?]
                 (if (and fast? (fn? (.-fastSeek element)))
                   (.fastSeek element to)
                   (set! (.-currentTime element) to)))
         seek! (fn [{:keys [fast-seek] :as details}]
                 (some-> (seek-position details
                                        (element-attributes element)
                                        offsets)
                         (go! fast-seek)))]
     (cond-> {:play   (fn [_]
                        ;; the promise rejects where the browser won't play,
                        ;; and nothing else would catch it
                        (some-> (.play element) (.catch (constantly nil))))
              :pause  (fn [_] (.pause element))
              :stop   (fn [_]
                        (.pause element)
                        (set! (.-currentTime element) 0))
              :seekto seek!}
       (:seekbackward offsets) (assoc :seekbackward seek!)
       (:seekforward offsets)  (assoc :seekforward seek!)))))

(defn attach!
  "Keep the Media Session in step with the media `element`, with the `opts`
  above and below, and return a function that detaches it.

  - :position-fn, a function from the element's :duration, :current-time,
    :playback-rate and :paused to its position state, e.g.
    element-position. Without it, the position state stays clear, so that
    the browser follows the element itself.

  At each of element-events, the call sets the playback state and the
  position state that element-state returns for the element. Detaching
  stops that, clears the position state and declares the playback state
  :none. Attaching another element detaches this one, since a page has
  one session."
  ([element]
   (attach! element {}))
  ([^js element {:keys [position-fn]
                 :or   {position-fn (constantly nil)}
                 :as   opts}]
   (if-let [^js session (media-session opts)]
     (let [st      (state session)
           show!   (fn [{:keys [playback-state position-state]}]
                     (playback-state! playback-state opts)
                     (position! position-state opts))
           follow! (fn [_]
                     (->> (element-attributes element)
                          (element-state position-fn)
                          show!))
           detach! (fn detach! []
                     ;; unless another attachment took over since
                     (when (identical? detach! (:detach @st))
                       (doseq [event element-events]
                         (.removeEventListener element event follow!))
                       (show! {:playback-state :none})
                       (swap! st dissoc :detach))
                     nil)]
       (when-let [previous (:detach @st)]
         (previous))
       (doseq [event element-events]
         (.addEventListener element event follow!))
       (swap! st assoc :detach detach!)
       (follow! nil)
       detach!)
     (constantly nil))))
