(ns dk.simongray.play-nice.tabs
  "One player at a time across the tabs of an app, through Web Locks.

  On a desktop, no engine stops the audio of one tab when another tab
  starts, so two tabs of a podcast app play at once and race to save the
  position. attach! holds a lock named for the app while the element
  plays, and takes it from any other tab that holds it, which then pauses
  its own element:

      (def detach!
        (attach! audio \"my-podcast-app\"))

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
  does nothing."
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
                         (hold! nil)))]
         (.addEventListener element "play" hold!)
         (.addEventListener element "pause" let-go!)
         (.addEventListener window "pagehide" let-go!)
         (.addEventListener window "pageshow" shown!)
         (when-not (.-paused element)
           (hold! nil))
         (fn []
           (.removeEventListener element "play" hold!)
           (.removeEventListener element "pause" let-go!)
           (.removeEventListener window "pagehide" let-go!)
           (.removeEventListener window "pageshow" shown!)
           (let-go! nil)
           nil))))))
