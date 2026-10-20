(ns dk.simongray.play-nice.page.tabs
  "One player at a time across the tabs of an app, through Web Locks.

  On a desktop, no engine stops the audio of one tab when another tab
  starts, so two tabs of a podcast app play at once and race to save the
  position. attach! holds a lock named for the app while the element
  plays, and takes it from any other tab that holds it, which then pauses
  its own element:

      (def detach!
        (attach! audio \"my-podcast-app\"))

  For an app that sends its playback to its other tabs itself, newest
  picks the newest of the playbacks that a tab received, and position
  moves one on to where it is by now.

  The functions take these opts:

  - :window, the window whose navigator holds the locks, instead of the
    page's, e.g. a stand-in in a test")

;; W3C Web Locks API, request() with steal
(defn attach!
  "Hold the Web Lock of `name` while the media `element` plays, with the
  `opts` above, and pause the element when another tab takes the lock.
  Return a function that detaches it.

  The element takes the lock from any holder when it plays, and lets it
  go when it pauses and at pagehide, since a held lock keeps a page out of
  the back/forward cache. Where the browser has no Web Locks, the call
  does nothing. Where the window fires no events, e.g. Node's global
  object, which has Web Locks from Node 24.5, the element still takes the
  lock as it plays and lets go of it as it pauses."
  ([element name]
   (attach! element name {}))
  ([^js element name opts]
   (let [^js window    (:window opts js/globalThis)
         ^js navigator (some-> window .-navigator)
         ^js locks     (some-> navigator .-locks)]
     (if-not (fn? (some-> locks .-request))
       (constantly nil)
       (let [;; the lock that the element wants, by a token of its own, so
             ;; that a lock it lets go of and takes again isn't stolen
             wanted  (atom nil)
             hold!   (fn [_]
                       (when-not @wanted
                         (let [token   #js {}
                               current #(identical? token (:token @wanted))
                               ;; held until let-go! resolves the promise
                               granted (fn [_]
                                         (js/Promise.
                                          (fn [resolve _]
                                            (if (current)
                                              (swap! wanted assoc :release resolve)
                                              (resolve nil)))))
                               ;; an AbortError, where another tab took it
                               stolen  (fn [_]
                                         (when (current)
                                           (reset! wanted nil)
                                           (.pause element)))]
                           (reset! wanted {:token token})
                           (.catch (.request locks name #js {:steal true} granted)
                                   stolen))))
             let-go! (fn [_]
                       (when-let [{:keys [release]} @wanted]
                         (reset! wanted nil)
                         (when release
                           (release nil))))
             shown!  (fn [^js e]
                       (when (and (.-persisted e) (not (.-paused element)))
                         (hold! nil)))
             paged?  (fn? (.-addEventListener window))]
         (.addEventListener element "play" hold!)
         (.addEventListener element "pause" let-go!)
         (when paged?
           (.addEventListener window "pagehide" let-go!)
           (.addEventListener window "pageshow" shown!))
         (when-not (.-paused element)
           (hold! nil))
         (fn []
           (.removeEventListener element "play" hold!)
           (.removeEventListener element "pause" let-go!)
           (when paged?
             (.removeEventListener window "pagehide" let-go!)
             (.removeEventListener window "pageshow" shown!))
           (let-go! nil)
           nil))))))

(def heartbeat-ms
  "How many milliseconds may pass between two playbacks that a tab sends
  while its element plays. newest counts the tab as gone after three."
  5000)

;; A tab that goes silent while its element plays, e.g. one that crashed,
;; leaves its playback paused where it was.
(defn newest
  "The newest of the playback `states` of the tabs at `now`, in
  milliseconds since the epoch: one that plays, and else the one that
  changed last. A playing one that hasn't told where it is for three
  heartbeats counts as paused, and :gone.

  Each state is a map of keys from media/state, with :at, the time in
  milliseconds at which its :current-time was taken, and :changed, when
  it last changed."
  [states now]
  (let [heard (for [{:keys [paused at] :as s} states
                    :when s]
                (if (and (false? paused) (number? at) (> (- now at) (* 3 heartbeat-ms)))
                  (assoc s :paused true :gone true)
                  s))]
    (or (first (filter #(false? (:paused %)) heard))
        (last (sort-by #(or (:changed %) 0) heard)))))

;; W3C Media Session, position state: a position moves on from the time it
;; was taken at its rate, and no further than the duration
;; https://w3c.github.io/mediasession/#position-state-sec
(defn position
  "Where the playback `state` is at `now`, in milliseconds since the epoch:
  its :current-time, moved on by the time since its :at at its rate while
  it plays, and no further than its duration."
  [{:keys [current-time at paused status playback-rate duration] :as state} now]
  (when (number? current-time)
    (let [moving? (and (false? paused) (= :ready status) (number? at))
          t       (cond-> current-time
                    moving? (+ (* (or playback-rate 1) (max 0 (/ (- now at) 1000)))))]
      (if (and (number? duration) (js/isFinite duration))
        (min t duration)
        t))))
