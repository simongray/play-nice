(ns dk.simongray.play-nice.session
  "The Media Session of the page, i.e. what the lock screen, the
  notification and the headphone buttons show and do for what plays.

  The calls set the page's session, and do nothing where the browser has
  none, e.g. in Node or the Android WebView. Each takes these opts:

  - :session, the MediaSession to set instead of the page's, e.g. a
    stand-in in a test, or nil for none

  The keys of the maps are the members of the spec's dictionaries in
  kebab-case, and the values of its enums are keywords, e.g. {:action
  :seekto :seek-time 754.2}. A player of the media element `audio` with no
  rules of its own needs three calls:

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
  "The events of a media element that attach! follows: those that change
  its duration or rate, make its position jump, start or stop it, or give
  it a new source."
  ["loadedmetadata" "durationchange" "play" "playing" "pause" "seeked"
   "ratechange" "ended" "emptied"])

(defn ^:no-doc finite?
  "Whether `x` is a number that's neither infinite nor NaN."
  [x]
  (js/Number.isFinite x))

(defn ^:no-doc within
  "The position `x` kept within 0 and the `duration`, where a duration that
  isn't finite has no end."
  [duration x]
  (cond-> (max 0 x)
    (finite? duration) (min duration)))

;; §9, MediaPositionState, and the steps of setPositionState in §5
(defn position-state
  "The position state of a player `position` seconds into a `duration` at
  `rate`, or nil while the duration is unknown.

  The position stays within the duration, and a rate that isn't a finite
  number other than 0 counts as 1, since setPositionState throws for
  either. An infinite duration is a live stream."
  [duration rate position]
  (when (and (number? duration) (pos? duration))
    {:duration      duration
     :playback-rate (if (and (finite? rate) (not (zero? rate))) rate 1)
     :position      (within duration (if (finite? position) position 0))}))

(defn element-position
  "The position state of a media element, from the map `element` of its
  :duration, :current-time and :playback-rate, or nil while its duration
  is unknown."
  [{:keys [duration current-time playback-rate] :as element}]
  (position-state duration playback-rate current-time))

(defn element-state
  "What the session shows for a media element, from the map `element` of
  its attributes: its :playback-state, and the :position-state that the
  function `position-fn` gives for the element, e.g. element-position."
  [position-fn {:keys [paused] :as element}]
  {:playback-state (if paused :paused :playing)
   :position-state (position-fn element)})

;; §10: seekTime for seekto, and a seekOffset that may be missing for
;; seekbackward and seekforward
(defn seek-position
  "The position that the seek action of `details` goes to on a media
  element, from the map `element` of its :current-time and :duration, or
  nil for another action or a seek without a time or an offset. The
  `offsets` give the seconds of seekbackward and seekforward where the
  details have none, e.g. {:seekbackward 15 :seekforward 30}."
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
  "Whether the `chapter` has a start time that Chromium takes: none, which
  means 0, or a finite number of 0 or more."
  [{t :start-time :as chapter}]
  (or (nil? t) (and (finite? t) (>= t 0))))

;; §6 to §8: MediaMetadataInit, ChapterInformationInit and MediaImage
(defn metadata
  "The metadata `m`, a map of the keys of MediaMetadataInit, without what a
  browser would throw for: the chapters with a :start-time that's
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
                   - :metadata, the last, and its MediaMetadata :object
                   - :detach, the function that ends the attachment
                   - :support, what support found"}
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
  browser takes the action."
  [^js session action f]
  (succeeds? #(.setActionHandler session
                                 (name action)
                                 (some-> f (comp details)))))

(defn support
  "What the Media Session supports, with the `opts` above, or nil where the
  browser has none:

  - :actions, the set of actions that it takes
  - :position-state?, whether it takes a position state
  - :chapter-info?, whether its metadata takes chapters

  The browser lists no actions, but throws for one it lacks. So the first
  call for a session clears each action that the library hasn't set, and
  keeps those that don't throw. Call it before you set any handler
  without the library."
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
           ;; what an earlier call set is cleared, unless set again
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
  "Show the metadata `m` in the Media Session, a map of the keys of
  MediaMetadataInit, or clear it for nil, with the `opts` above. Return the
  metadata as set, or nil where the browser has no Media Session.

  The metadata as set is without what the browser would throw for: what
  metadata removes, and the images whose URL doesn't parse. A browser
  without chapters ignores them. Each change makes a new MediaMetadata,
  which is the one way that works alike in every engine, but the same
  metadata twice in a row changes nothing."
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
  "Set the position `state` of the Media Session, a map of the keys of
  MediaPositionState, or clear it for nil, with the `opts` above. Return
  the state as set, or nil.

  The state goes through position-state first. Where the browser refuses
  it all the same, e.g. an infinite duration in Safari and Firefox, the
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
  "Handlers that play, pause, stop and seek the media `element` itself,
  with the seconds of the `offsets` of seekbackward and seekforward, e.g.
  {:seekbackward 15 :seekforward 30}. A skip without an offset is left
  out, since the spec gives none.

  Merge your own handlers over them for actions!. Safari drops its own
  buttons once a page sets a handler, so it needs seekto among them for a
  scrubber."
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

  At each of element-events, the call shows the element-state of the
  element. Detaching stops that, clears the position state and declares
  the playback state :none. Attaching another element detaches this one,
  since a page has one session."
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
