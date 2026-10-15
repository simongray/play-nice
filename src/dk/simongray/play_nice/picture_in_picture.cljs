(ns dk.simongray.play-nice.picture-in-picture
  "Picture-in-Picture of a video: whether it's available, entering and
  leaving it, and the size of its window, as data.

  Each engine refuses a video in its own words, e.g. a video with
  disablePictureInPicture throws an InvalidStateError in Chrome and
  Firefox, but a NotSupportedError in WebKit. So enter! checks the video
  first, and answers with one keyword for each cause:

      (-> (enter! video)
          (.then #(swap! app assoc :pip %)))
      ;; {:width 320 :height 180}, or e.g. {:refused :not-allowed}"
  (:require [dk.simongray.play-nice.media :as media]))

;; W3C Picture-in-Picture, Working Draft of 16 June 2026
(defn- obstacle
  "Why the `video` can't enter Picture-in-Picture, as a keyword, or nil
  where nothing stands in the way as far as the video shows."
  [^js video]
  (let [^js document (.-ownerDocument video)]
    (cond
      (not (and (fn? (.-requestPictureInPicture video))
                (some-> document .-pictureInPictureEnabled)))
      :not-supported

      (.-disablePictureInPicture video)
      :disabled

      (and (pos? (.-readyState video)) (zero? (.-videoWidth video)))
      :no-video)))

(defn available?
  "Whether the `video` can enter Picture-in-Picture: the browser has it,
  the page may use it, and the video has a video track and doesn't
  disable it. A video without its metadata yet counts as available."
  [video]
  (nil? (obstacle video)))

(defn in-picture-in-picture?
  "Whether the `video` is in Picture-in-Picture now."
  [^js video]
  (let [^js document (.-ownerDocument video)]
    (identical? video (some-> document .-pictureInPictureElement))))

(defn- request!
  "A promise of the answer to the `video`'s request for Picture-in-Picture,
  made at once, so that the click that called it still counts."
  [^js video]
  (-> (js/Promise. (fn [resolve _] (resolve (.requestPictureInPicture video))))
      (.then (fn [^js window]
               {:width (.-width window) :height (.-height window)})
             (fn [e]
               (let [refused (media/refusal e)]
                 ;; §4.1 says NotAllowedError for a page that may not, and
                 ;; §5.2 and Chromium SecurityError
                 (if (#{:not-allowed :security} (:refused refused))
                   {:refused :not-allowed}
                   refused))))))

(defn- attempt!
  "A promise of the answer to entering Picture-in-Picture with the `video`,
  once its metadata has loaded."
  [video]
  (if-let [reason (obstacle video)]
    (js/Promise.resolve {:refused reason})
    (request! video)))

(defn- metadata
  "A promise that the `video` has loaded its metadata, or failed to."
  [^js video]
  (js/Promise. (fn [resolve _]
                 (doseq [event ["loadedmetadata" "error"]]
                   (.addEventListener video event resolve #js {:once true})))))

(defn enter!
  "Show the `video` in Picture-in-Picture, and return a promise of the
  answer, which never rejects:

  - the size of the window, e.g. {:width 320 :height 180}
  - {:refused :not-allowed} without a click, or where the page may not
  - {:refused :not-supported} where the browser has no Picture-in-Picture
    for it, e.g. for an audio element
  - {:refused :disabled} for a video with disablePictureInPicture
  - {:refused :no-video} for a video without a video track

  Call it in a click handler, since Chrome and WebKit refuse it otherwise. A video without its metadata yet enters once it has loaded
  it, as long as the click still counts, e.g. for 5 seconds in Chrome."
  [^js video]
  (if (and (nil? (obstacle video)) (zero? (.-readyState video)))
    (.then (metadata video) #(attempt! video))
    (attempt! video)))

(defn exit!
  "Take the `video` out of Picture-in-Picture, if it's in it, and return a
  promise of {:picture-in-picture false}, which never rejects."
  [^js video]
  (let [^js document (.-ownerDocument video)
        left         {:picture-in-picture false}]
    (if (in-picture-in-picture? video)
      (-> (js/Promise. (fn [resolve _]
                         (resolve (.exitPictureInPicture document))))
          (.then (constantly left) media/refusal))
      (js/Promise.resolve left))))

(defn watch!
  "Call `f` with the Picture-in-Picture state of the `video` now and at
  each enter, resize and leave, and return a function that stops it. The
  state is {:picture-in-picture true :width 320 :height 180} while it's in
  Picture-in-Picture, and {:picture-in-picture false} otherwise.

  The size comes from the window at each enter and resize, so a video in
  Picture-in-Picture before the call has none until its window resizes.
  Firefox leaves only after the promise of exit! has resolved."
  [^js video f]
  (let [shown   (atom nil)
        window  (atom nil)
        show!   (fn [m]
                  (when (not= m @shown)
                    (reset! shown m)
                    (f m)))
        sized   (fn [^js w]
                  {:picture-in-picture true
                   :width              (.-width w)
                   :height             (.-height w)})
        resize! (fn [^js e] (show! (sized (.-target e))))
        follow! (fn [^js w]
                  (some-> ^js @window (.removeEventListener "resize" resize!))
                  (reset! window w)
                  (some-> w (.addEventListener "resize" resize!)))
        enter   (fn [^js e]
                  (let [^js w (.-pictureInPictureWindow e)]
                    (follow! w)
                    (show! (if w (sized w) {:picture-in-picture true}))))
        leave   (fn [_]
                  (follow! nil)
                  (show! {:picture-in-picture false}))]
    (.addEventListener video "enterpictureinpicture" enter)
    (.addEventListener video "leavepictureinpicture" leave)
    (show! {:picture-in-picture (in-picture-in-picture? video)})
    (fn []
      (.removeEventListener video "enterpictureinpicture" enter)
      (.removeEventListener video "leavepictureinpicture" leave)
      (follow! nil)
      nil)))
