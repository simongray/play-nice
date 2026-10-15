(ns dk.simongray.play-nice.viewport
  "Autoplay of a video while it's in view, alike in every engine.

  Chrome and WebKit on iOS pause a muted video with the autoplay attribute
  when it scrolls out of view, and play it again when it returns, but
  leave alone a video that the page plays itself. autoplay! does the same
  for any video, through media/play!:

      (def stop!
        (autoplay! hero #(swap! page assoc :hero %) {:threshold 0.5}))"
  (:require [dk.simongray.play-nice.media :as media]))

(defn reduced-motion?
  "Whether the user prefers reduced motion, as the media query
  prefers-reduced-motion says, or false where the browser can't tell."
  []
  (boolean
   (when (fn? (.-matchMedia js/globalThis))
     (.-matches (js/globalThis.matchMedia "(prefers-reduced-motion: reduce)")))))

;; W3C Intersection Observer, and the engines' own rule for a muted video
;; with the autoplay attribute out of view
(defn autoplay!
  "Play the `video` while it's in view, and pause it while it isn't, with
  the `opts` below, and return a function that stops it. Call `f` with the
  state of the video now and whenever it changes, e.g.

      {:visible true :playing true}

  The video plays as media/play! with :muted-fallback plays it, and pauses
  out of view only while it's muted, as the engines do. Once the visitor
  pauses the video, the call leaves it to them, and the state holds :held
  as :visitor. Where the user prefers reduced motion, the call holds the
  video from the start, as :reduced-motion. A play that the browser
  refused shows as :refused, e.g. :not-allowed.

  - :threshold, the share of the video that must show for it to play,
    0.5 by default
  - :reduced-motion, whether to hold the video, by default as the user's
    preference says, from reduced-motion?"
  ([video f]
   (autoplay! video f {}))
  ([^js video f {:keys [threshold reduced-motion]
                 :or   {threshold 0.5}
                 :as   opts}]
   (let [held    (atom (when (if (some? reduced-motion)
                                   reduced-motion
                                   (reduced-motion?))
                         :reduced-motion))
         visible (atom false)
         refused (atom nil)
         pausing (atom false)
         shown   (atom nil)
         show!   (fn [_]
                   (let [m (cond-> {:visible @visible
                                    :playing (not (.-paused video))}
                             @held    (assoc :held @held)
                             @refused (assoc :refused @refused))]
                     (when (not= m @shown)
                       (reset! shown m)
                       (f m))))
         play!   (fn []
                   (.then (media/play! video {:muted-fallback true})
                          (fn [answer]
                            (reset! refused (:refused answer))
                            (show! nil))))
         pause!  (fn []
                   (when-not (.-paused video)
                     (reset! pausing true)
                     (.pause video)))
         paused  (fn [_]
                   ;; a pause that autoplay! didn't make, and not the end,
                   ;; is the visitor's
                   (cond
                     @pausing        (reset! pausing false)
                     (.-ended video) nil
                     :else           (reset! held :visitor))
                   (show! nil))
         seen    (fn [entries]
                   (let [^js entry (last entries)]
                     (reset! visible (and (.-isIntersecting entry)
                                          (>= (.-intersectionRatio entry)
                                              (* 0.99 threshold))))
                     (cond
                       @held          nil
                       @visible       (play!)
                       (.-muted video) (pause!))
                     (show! nil)))
         ;; only an element in a browser has a place in the viewport
         ^js observer (when (and (exists? js/IntersectionObserver)
                                 (instance? js/Element video))
                        (js/IntersectionObserver. seen
                                                  #js {:threshold threshold}))]
     (.addEventListener video "pause" paused)
     (.addEventListener video "play" show!)
     (some-> observer (.observe video))
     (show! nil)
     (fn []
       (some-> observer .disconnect)
       (.removeEventListener video "pause" paused)
       (.removeEventListener video "play" show!)
       nil))))
