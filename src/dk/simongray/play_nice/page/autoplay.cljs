(ns dk.simongray.play-nice.page.autoplay
  "The browser's autoplay policy, the wait for the click, tap or key press
  after which the page may play, and autoplay of a video while it's in
  view.

  The policy and the wait take these opts:

  - :window, the window to ask and listen at instead of the page's, e.g.
    a stand-in in a test

  Only Firefox has Autoplay Policy Detection, so elsewhere you find out by
  calling media/play!, which answers {:refused :not-allowed} where the
  browser won't play. play-when-allowed! plays then at the user's next
  click, tap or key press.

  Chrome and WebKit on iOS pause a muted video with the autoplay attribute
  when it scrolls out of view, and play it again when it returns, but
  leave alone a video that the page plays itself. play-in-view! does the
  same for any video, in every engine, through media/play!:

      (def stop!
        (play-in-view! hero #(swap! page assoc :hero %) {:threshold 0.5}))"
  (:require [dk.simongray.play-nice.media :as media]
            [dk.simongray.play-nice.util.keys :as keys]))

;; Autoplay Policy Detection, Working Draft of 4 September 2025, §3
(defn policy
  "The browser's autoplay policy for `target`, with the `opts` above:
  :allowed, :allowed-muted or :disallowed, or nil where it can't tell.

  The `target` is a media element, an AudioContext, or either's kind,
  :mediaelement or :audiocontext."
  ([target]
   (policy target {}))
  ([target opts]
   (let [^js window    (:window opts js/globalThis)
         ^js navigator (some-> window .-navigator)]
     (when (fn? (some-> navigator .-getAutoplayPolicy))
       (try
         (keyword (.getAutoplayPolicy navigator (keys/->js target)))
         (catch :default _ nil))))))

;; HTML §6.4.3: the activation-triggering input events
(def activation-events
  "The events at which a page may gain user activation."
  [:keydown :mousedown :pointerdown :pointerup :touchend])

(defn on-activation!
  "Call `f` with no arguments once the page has user activation, with the
  `opts` above, and return a function that stops waiting.

  It calls `f` at once where the page has activation now, e.g. in a click
  handler, and otherwise at the user's next click, tap or key press, e.g.
  to start the media that the browser refused to play:

      (on-activation! #(media/play! audio))

  Not every input activates the page, e.g. the Escape key doesn't, so the
  call asks the browser at each of activation-events. Where the browser
  can't tell, any of them counts."
  ([f]
   (on-activation! f {}))
  ([f opts]
   (let [^js window     (:window opts js/globalThis)
         ^js navigator  (some-> window .-navigator)
         ^js activation (some-> navigator .-userActivation)]
     (cond
       (some-> activation .-isActive)
       (do (f)
           (constantly nil))

       (fn? (some-> window .-addEventListener))
       ;; in the capture phase, before a handler can stop the event
       (let [capture #js {:capture true}]
         (letfn [(stop! []
                   (doseq [event activation-events]
                     (.removeEventListener window (name event) listen! capture))
                   nil)
                 (listen! [_]
                   (when (or (nil? activation) (.-isActive activation))
                     (stop!)
                     (f)))]
           (doseq [event activation-events]
             (.addEventListener window (name event) listen! capture))
           stop!))

       :else
       (constantly nil)))))

(defn play-when-allowed!
  "Play the media `element` now, or where the browser refuses, once more at
  the user's next click, tap or key press, with the `opts` above and below.
  Call `f` with each answer of media/play!, and return a function that
  stops it, e.g.

      (def stop!
        (play-when-allowed! audio #(swap! app assoc :answer %)))

  The element plays muted where the browser refuses only its sound, which
  answers {:playing true :muted true}. Where it refuses even that, `f`
  gets {:refused :not-allowed :waiting true} while the call waits, and
  then the answer of the second try. There's no third, so where the
  browser never lets the page play, e.g. by Safari's Never Auto-Play
  setting, the wait ends with its refusal rather than loop.

  - :muted-fallback, false to wait for the user rather than play muted,
    true by default"
  ([element f]
   (play-when-allowed! element f {}))
  ([element f {:keys [muted-fallback] :or {muted-fallback true} :as opts}]
   (let [stopped (atom false)
         waiting (atom nil)
         answer! #(when-not @stopped (f %))
         play!   #(media/play! element {:muted-fallback muted-fallback})]
     (.then (play!)
            (fn [answer]
              (if (= :not-allowed (:refused answer))
                (when-not @stopped
                  (f (assoc answer :waiting true))
                  (reset! waiting (on-activation! #(.then (play!) answer!)
                                                  opts)))
                (answer! answer))))
     (fn []
       (reset! stopped true)
       (when-let [stop! @waiting]
         (stop!))
       nil))))

(defn reduced-motion?
  "Whether the user prefers reduced motion, as the media query
  prefers-reduced-motion says, or false where the browser can't tell."
  []
  (boolean
   (when (fn? (.-matchMedia js/globalThis))
     (.-matches (js/globalThis.matchMedia "(prefers-reduced-motion: reduce)")))))

;; W3C Intersection Observer, and the engines' own rule for a muted video
;; with the autoplay attribute out of view
(defn play-in-view!
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
   (play-in-view! video f {}))
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
                   ;; a pause that play-in-view! didn't make, and not the
                   ;; end, is the visitor's
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
