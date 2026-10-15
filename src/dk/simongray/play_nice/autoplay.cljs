(ns dk.simongray.play-nice.autoplay
  "The browser's autoplay policy, and the wait for the click, tap or key
  press after which the page may play.

  The functions take these opts:

  - :window, the window to ask and listen at instead of the page's, e.g.
    a stand-in in a test

  Only Firefox has Autoplay Policy Detection, so elsewhere you find out by
  calling media/play!, which answers {:refused :not-allowed} where the
  browser won't play."
  (:require [dk.simongray.play-nice.keys :as keys]))

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
