(ns dk.simongray.play-nice.controls
  "Keyboard shortcuts and a seek bar for a player of your own, which press
  the same handlers as the lock screen.

  A shortcut of a single key that types a character must work only while
  the player has the focus, unless the user can turn it off or remap it
  (WCAG 2.2, §2.1.4). So listen! follows the keys pressed in an element of
  your choice, e.g. the one that holds the player, and calls the handlers
  of the Media Session with the details of each action:

      (def handlers
        (session/element-actions audio {:seekbackward 10 :seekforward 30}))

      (session/actions! handlers)

      (def stop!
        (listen! player audio handlers))

  Everything but listen! runs on the JVM too, e.g. to write a seek bar's
  ARIA attributes into a page on the server."
  (:require [clojure.string :as str]
            #?(:cljs [dk.simongray.play-nice.media :as media])))

(defn play-or-pause
  "The details of the action that a play/pause key presses for a media
  element in `state`, a map of its :paused: {:action :play} while it's
  paused, and {:action :pause} otherwise."
  [{:keys [paused] :as state}]
  {:action (if paused :play :pause)})

;; UI Events, the key values of KeyboardEvent, and the keys of YouTube
(def player-keys
  "The shortcuts of listen! by default: a map from each key, as the key of
  a KeyboardEvent names it, to the action it presses, or to a function
  from the state of the media element to the details of the action."
  {" "                  play-or-pause
   "k"                  play-or-pause
   "MediaPlayPause"     play-or-pause
   "MediaPlay"          :play
   "MediaPause"         :pause
   "MediaStop"          :stop
   "j"                  :seekbackward
   "ArrowLeft"          :seekbackward
   "MediaRewind"        :seekbackward
   "l"                  :seekforward
   "ArrowRight"         :seekforward
   "MediaFastForward"   :seekforward
   "MediaTrackPrevious" :previoustrack
   "MediaTrackNext"     :nexttrack})

(defn seek-by
  "A function from the state of a media element, a map of its
  :current-time, to the details of a seek by `seconds` from there, e.g.
  -15 to go back."
  [seconds]
  (fn [{:keys [current-time] :as state}]
    {:action :seekto :seek-time (+ current-time seconds)}))

;; WAI-ARIA Authoring Practices, the Media Seek Slider example
(def seek-bar-keys
  "The keys of a seek bar, as the APG's Media Seek Slider has them: a
  second for each arrow, 15 for each page, and the start and the end for
  Home and End."
  {"ArrowRight" (seek-by 1)
   "ArrowUp"    (seek-by 1)
   "ArrowLeft"  (seek-by -1)
   "ArrowDown"  (seek-by -1)
   "PageUp"     (seek-by 15)
   "PageDown"   (seek-by -15)
   "Home"       (constantly {:action :seekto :seek-time 0})
   "End"        (fn [{:keys [duration]}]
                  {:action :seekto :seek-time duration})})

#?(:cljs
   (defn- foreign?
     "Whether the keydown `e` belongs to something other than the shortcuts:
     a key with Alt, Ctrl or Meta, one that a handler took already, typing
     in a field, or a space that presses a button."
     [^js e]
     (let [^js target (.-target e)
           matches?   #(and (fn? (.-matches target)) (.matches target %))]
       (boolean (or (.-defaultPrevented e)
                    (.-altKey e)
                    (.-ctrlKey e)
                    (.-metaKey e)
                    (.-isContentEditable target)
                    (matches? "input, textarea, select")
                    (and (= " " (.-key e))
                         (matches? "button, summary, [role=button]")))))))

#?(:cljs
   (defn listen!
     "Call the `handlers` at the keys pressed while the focus is in `scope`,
     for the media `element`, and return a function that stops it. The
     `keymap` is player-keys by default, or e.g. seek-bar-keys for a seek
     bar of its own.

     A handler gets the details of its action, as from the lock screen,
     e.g. {:action :seekforward}, and the browser does nothing else for
     that key, e.g. scroll at the space bar. The call leaves alone the keys
     that belong to something else, i.e. those with Alt, Ctrl or Meta,
     those that a handler took already, typing in a field, and a space that
     presses a button. Holding a key down repeats only a seek."
     ([scope element handlers]
      (listen! scope element handlers player-keys))
     ([^js scope ^js element handlers keymap]
      (if (fn? (some-> scope .-addEventListener))
        (let [seeks  #{:seekbackward :seekforward :seekto}
              press! (fn [^js e]
                       (when-let [v (and (not (foreign? e))
                                         (get keymap (.-key e)))]
                         (let [details (if (keyword? v)
                                         {:action v}
                                         (v (media/state element)))
                               action  (:action details)
                               f       (get handlers action)]
                           (when (and f (or (not (.-repeat e)) (seeks action)))
                             (.preventDefault e)
                             (f details)))))]
          (.addEventListener scope "keydown" press!)
          (fn []
            (.removeEventListener scope "keydown" press!)
            nil))
        (constantly nil)))))

(defn- whole
  "The whole seconds of `x`, rounded down, or nil where `x` isn't a finite
  number."
  [x]
  (when #?(:clj  (and (number? x) (Double/isFinite (double x)))
           :cljs (js/Number.isFinite x))
    (long (Math/floor (double x)))))

(defn- english
  "The `parts` of a duration, a map of its :hours, :minutes and :seconds,
  in English words, as Intl.DurationFormat writes them, e.g. \"1 hour, 5
  seconds\"."
  [{:keys [hours minutes seconds] :as parts}]
  (let [unit (fn [n one] (str n " " one (when (not= 1 n) "s")))]
    (str/join ", " (cond-> []
                     (pos? hours)   (conj (unit hours "hour"))
                     (pos? minutes) (conj (unit minutes "minute"))

                     (or (pos? seconds) (= 0 hours minutes))
                     (conj (unit seconds "second"))))))

(defn value-text
  "The `seconds` in words for a screen reader, e.g. \"12 minutes, 30
  seconds\" for 750, in the `locale`, by default the browser's, e.g.
  \"12 minutter og 30 sekunder\" in \"da\". Where the browser has no
  Intl.DurationFormat, and on the JVM, the words are English."
  ([seconds]
   (value-text seconds nil))
  ;; the JVM has no DurationFormat, so English is all it writes
  ([seconds #_{:clj-kondo/ignore [:unused-binding]} locale]
   (let [s     (max 0 (or (whole seconds) 0))
         parts {:hours   (quot s 3600)
                :minutes (quot (mod s 3600) 60)
                :seconds (mod s 60)}]
     #?(:clj  (english parts)
        :cljs (try
                (.format (js/Intl.DurationFormat.
                          (or locale js/undefined)
                          #js {:style          "long"
                               :secondsDisplay (if (zero? s) "always" "auto")})
                         (clj->js parts))
                (catch :default _
                  (english parts)))))))

;; WAI-ARIA 1.2, the slider role
(defn seek-aria
  "The ARIA attributes of a seek bar for a media element in `state`, a map
  of its :current-time and :duration, e.g. from media/state, with the
  position in words from value-text in the `locale`, e.g.

      {:role           \"slider\"
       :aria-valuemin  0
       :aria-valuemax  3600
       :aria-valuenow  750
       :aria-valuetext \"12 minutes, 30 seconds\"}

  The seek bar needs a name of its own too, e.g. an :aria-label. While the
  duration is unknown or infinite, it has no maximum."
  ([state]
   (seek-aria state nil))
  ([{:keys [current-time duration] :as state} locale]
   (let [now (or (whole current-time) 0)]
     (cond-> {:role           "slider"
              :aria-valuemin  0
              :aria-valuenow  now
              :aria-valuetext (value-text now locale)}
       (whole duration) (assoc :aria-valuemax (whole duration))))))
