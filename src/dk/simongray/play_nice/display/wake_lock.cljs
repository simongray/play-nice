(ns dk.simongray.play-nice.display.wake-lock
  "The screen kept on while a media element plays, through Screen Wake
  Lock.

  A playing video keeps the screen on by itself, but no engine does that
  for audio, so a page that shows a transcript or lyrics while an episode
  plays goes dark:

      (def stop!
        (hold-while-playing! audio))

  The functions take these opts:

  - :window, the window whose navigator holds the lock, instead of the
    page's, e.g. a stand-in in a test")

;; W3C Screen Wake Lock API, request(\"screen\") and the sentinel's release
(defn hold-while-playing!
  "Keep the screen on while the media `element` plays and its page shows,
  with the `opts` above, and return a function that stops it.

  The browser lets go of the lock whenever the page is hidden, so the
  call requests it again when the page shows again. It lets go itself at
  a pause or the end. Where the browser has no Screen Wake Lock, or
  refuses the lock, e.g. on a low battery, the screen goes dark as it
  would have."
  ([element]
   (hold-while-playing! element {}))
  ([^js element opts]
   (let [^js window    (:window opts js/globalThis)
         ^js navigator (some-> window .-navigator)
         ^js wake-lock (some-> navigator .-wakeLock)
         ^js document  (.-ownerDocument element)]
     (if-not (and document (fn? (some-> wake-lock .-request)))
       (constantly nil)
       (let [;; the sentinel of the lock, or :pending while it's requested
             held    (atom nil)
             wanted? #(and (not (.-paused element))
                           (= "visible" (.-visibilityState document)))
             let-go! (fn [_]
                       (let [^js sentinel @held]
                         (reset! held nil)
                         (when (and sentinel (not= :pending sentinel))
                           (.release sentinel))))
             hold!   (fn [_]
                       (when (and (wanted?) (nil? @held))
                         (reset! held :pending)
                         (.then (.request wake-lock "screen")
                                (fn [^js sentinel]
                                  (if (and (= :pending @held) (wanted?))
                                    (do (reset! held sentinel)
                                        ;; as the browser lets go of it
                                        (.addEventListener
                                         sentinel "release"
                                         #(when (identical? sentinel @held)
                                            (reset! held nil))))
                                    (.release sentinel)))
                                (fn [_]
                                  (when (= :pending @held)
                                    (reset! held nil))))))]
         (.addEventListener element "play" hold!)
         (.addEventListener element "pause" let-go!)
         (.addEventListener document "visibilitychange" hold!)
         (hold! nil)
         (fn []
           (.removeEventListener element "play" hold!)
           (.removeEventListener element "pause" let-go!)
           (.removeEventListener document "visibilitychange" hold!)
           (let-go! nil)
           nil))))))
