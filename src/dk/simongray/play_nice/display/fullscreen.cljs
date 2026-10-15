(ns dk.simongray.play-nice.display.fullscreen
  "Fullscreen of a player, with the fallback that an iPhone needs, where
  only a video element itself can go fullscreen, in the system's own
  player.

  The functions take these opts:

  - :video, the video element of the player, for that fallback

      (-> (enter! player {:video video :orientation :landscape})
          (.then #(swap! page assoc :fullscreen %)))
      ;; {:fullscreen :element :orientation {:refused :not-supported}}
      ;; on a desktop, or {:fullscreen :video …} on an iPhone"
  (:require [dk.simongray.play-nice.media :as media]))

;; WHATWG Fullscreen Living Standard, and WebKit's own video fullscreen
(defn state
  "The fullscreen of the `element`, with the `opts` above, as a map of its
  :fullscreen: :element while the element is fullscreen, :video while its
  video is in the system's own player, and false otherwise."
  ([element]
   (state element {}))
  ([^js element {:keys [video] :as opts}]
   (let [^js document (.-ownerDocument element)
         ^js video    video]
     {:fullscreen (cond
                    (identical? element (some-> document .-fullscreenElement))
                    :element

                    (some-> video .-webkitDisplayingFullscreen)
                    :video

                    :else
                    false)})))

;; W3C Screen Orientation, Working Draft of 6 August 2026
(defn lock-orientation!
  "Lock the screen in the `orientation`, e.g. :landscape, and return a
  promise of the answer, which never rejects: {:locked :landscape}, or
  e.g. {:refused :not-supported} on a desktop. A browser locks the screen
  only while the page is fullscreen, and Firefox refuses it otherwise as
  {:refused :not-allowed}."
  [orientation]
  (let [^js screen             (.-screen js/globalThis)
        ^js screen-orientation (some-> screen .-orientation)]
    (if (fn? (some-> screen-orientation .-lock))
      (-> (js/Promise. (fn [resolve _]
                         (resolve (.lock screen-orientation (name orientation)))))
          (.then (constantly {:locked orientation})
                 (fn [e]
                   (let [refused (media/refusal e)]
                     (if (= :security (:refused refused))
                       {:refused :not-allowed}
                       refused)))))
      (js/Promise.resolve {:refused :not-supported}))))

(defn- enter-video!
  "A promise of the answer to showing the `video` in the system's own
  player, asked at once, so that the tap that called it still counts."
  [^js video]
  (js/Promise.resolve
   (cond
     (zero? (.-readyState video))
     {:refused :no-metadata}

     (not (.-webkitSupportsFullscreen video))
     {:refused :not-supported}

     :else
     (try
       (.webkitEnterFullscreen video)
       {:fullscreen :video}
       (catch :default e
         ;; WebKit throws an InvalidStateError without a tap
         (let [refused (media/refusal e)]
           (if (= :invalid-state (:refused refused))
             {:refused :not-allowed}
             refused)))))))

(defn- with-orientation
  "The `answer` of entering fullscreen, or a promise of it with the answer
  of locking the screen in the `orientation` as :orientation."
  [answer orientation]
  (if (and orientation (:fullscreen answer))
    (.then (lock-orientation! orientation) #(assoc answer :orientation %))
    answer))

(defn enter!
  "Put the `element` fullscreen, with the `opts` above and below, and
  return a promise of the answer, which never rejects:

  - {:fullscreen :element} once the element is fullscreen
  - {:fullscreen :video} where only the video can be, e.g. on an iPhone
  - {:refused :not-allowed} without a click, or where the page may not,
    e.g. in an iframe whose allow attribute leaves out fullscreen
  - {:refused :no-metadata} for a video without its metadata yet, which an
    iPhone can't show in its player
  - {:refused :not-supported} where neither can go fullscreen

  The options:

  - :orientation, e.g. :landscape, to lock the screen in once fullscreen,
    whose answer from lock-orientation! the answer holds as :orientation

  Call it in a click handler, since every engine refuses it otherwise."
  ([element]
   (enter! element {}))
  ([^js element {:keys [video orientation] :as opts}]
   (.then (cond
            (fn? (.-requestFullscreen element))
            (-> (js/Promise. (fn [resolve _] (resolve (.requestFullscreen element))))
                ;; the spec rejects with a TypeError for every reason, of
                ;; which a missing click is the usual one
                (.then (constantly {:fullscreen :element})
                       (constantly {:refused :not-allowed})))

            (and video (fn? (.-webkitEnterFullscreen ^js video)))
            (enter-video! video)

            :else
            (js/Promise.resolve {:refused :not-supported}))
          #(with-orientation % orientation))))

(defn exit!
  "Leave the fullscreen of the `element`, with the `opts` above, and return
  a promise of its state afterwards, which never rejects, e.g.
  {:fullscreen false}.

  In the iOS simulator, a video that has never played enters the system's
  player but can't leave it, which the state then shows as :video. Play
  the video in the tap that enters, as the player's own button does."
  ([element]
   (exit! element {}))
  ([^js element {:keys [video] :as opts}]
   (let [^js document (.-ownerDocument element)
         ^js video    video]
     (case (:fullscreen (state element opts))
       :element (-> (js/Promise. (fn [resolve _] (resolve (.exitFullscreen document))))
                    (.then #(state element opts) media/refusal))
       :video   (do (.webkitExitFullscreen video)
                    (js/Promise.resolve (state element opts)))
       (js/Promise.resolve {:fullscreen false})))))

(defn watch!
  "Call `f` with the state of the `element` now and whenever its fullscreen
  changes, with the `opts` above, and return a function that stops it,
  e.g. {:fullscreen :video}."
  ([element f]
   (watch! element f {}))
  ([^js element f {:keys [video] :as opts}]
   (let [^js document (.-ownerDocument element)
         ^js video    video
         events       ["webkitbeginfullscreen" "webkitendfullscreen"]
         shown        (atom nil)
         show!        (fn [_]
                        (let [m (state element opts)]
                          (when (not= m @shown)
                            (reset! shown m)
                            (f m))))]
     (.addEventListener document "fullscreenchange" show!)
     (when video
       (doseq [event events]
         (.addEventListener video event show!)))
     (show! nil)
     (fn []
       (.removeEventListener document "fullscreenchange" show!)
       (when video
         (doseq [event events]
           (.removeEventListener video event show!)))
       nil))))
