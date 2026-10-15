(ns dk.simongray.play-nice.display.remote-playback
  "Casting and AirPlay of a media element, from Remote Playback: whether a
  device is available, the browser's picker of devices, and the connection
  to one, as data.

  Firefox has no Remote Playback, so there no device is available and
  prompt! answers {:refused :not-supported}. WebKit also fires its older
  AirPlay events, which watch! follows as well, since Video.js finds the
  standard events unreliable in Safari."
  (:require [dk.simongray.play-nice.media :as media]))

;; W3C Remote Playback API, Candidate Recommendation Draft of 30 April 2024
(defn state
  "The remote playback of the media `element` now, as watch! passes it,
  e.g. {:availability :unknown :state :disconnected}.

  The :state is the connection to a remote device: :disconnected,
  :connecting or :connected. Only watch! learns whether a device is
  available, so the :availability is :unknown where the browser has Remote
  Playback, and :unavailable where it hasn't."
  [^js element]
  (let [^js remote (.-remote element)]
    {:availability (if remote :unknown :unavailable)
     :state        (cond
                     (.-webkitCurrentPlaybackTargetIsWireless element) :connected
                     remote (keyword (.-state remote))
                     :else  :disconnected)}))

(defn watch!
  "Call `f` with the remote playback of the media `element` now and
  whenever it changes, and return a function that stops it, e.g.
  {:availability :available :state :disconnected}.

  The :availability is :available or :unavailable as the browser says, and
  :unknown until it has, or where it looks for devices only while its
  picker is open. The :state is :disconnected, :connecting or :connected,
  as in state."
  [^js element f]
  (let [^js remote (.-remote element)
        stopped    (atom false)
        id         (atom nil)
        available  (atom (if remote :unknown :unavailable))
        shown      (atom nil)
        show!      (fn [_]
                     (let [m {:availability @available
                             :state        (:state (state element))}]
                       (when (and (not @stopped) (not= m @shown))
                         (reset! shown m)
                         (f m))))
        available! (fn [a]
                     (reset! available a)
                     (show! nil))
        webkit!    (fn [^js e]
                     (available! (if (= "available" (.-availability e))
                                   :available
                                   :unavailable)))
        events     {"webkitplaybacktargetavailabilitychanged"     webkit!
                    "webkitcurrentplaybacktargetiswirelesschanged" show!}
        cancel!    (fn [watch-id]
                     (.catch (.cancelWatchAvailability remote watch-id)
                             (constantly nil)))]
    (doseq [[event g] events]
      (.addEventListener element event g))
    (when remote
      (doseq [event ["connecting" "connect" "disconnect"]]
        (.addEventListener remote event show!))
      (-> (js/Promise.
           (fn [resolve _]
             (resolve (.watchAvailability remote
                                          #(available! (if % :available :unavailable))))))
          (.then (fn [watch-id]
                   (if @stopped
                     (cancel! watch-id)
                     (reset! id watch-id)))
                 (fn [e]
                   ;; NotSupportedError where it looks only in its picker
                   (available! (if (= :not-supported (:refused (media/refusal e)))
                                 :unknown
                                 :unavailable))))))
    (show! nil)
    (fn []
      (reset! stopped true)
      (doseq [[event g] events]
        (.removeEventListener element event g))
      (when remote
        (doseq [event ["connecting" "connect" "disconnect"]]
          (.removeEventListener remote event show!))
        (some-> @id cancel!))
      nil)))

(defn prompt!
  "Show the browser's picker of remote devices for the media `element`,
  e.g. at a click of a cast button, and return a promise of the answer,
  which never rejects:

  - {:state :connecting}, or another state, once the user has picked
  - {:refused :not-allowed} without a click
  - {:refused :dismissed} where the user closed the picker, which Chrome
    also answers without a click
  - {:refused :no-devices} where there's no device to pick
  - {:refused :disabled} for an element with disableremoteplayback
  - {:refused :not-supported} where the browser has no Remote Playback,
    e.g. Firefox"
  [^js element]
  (let [^js remote (.-remote element)]
    (cond
      (nil? remote)
      (js/Promise.resolve {:refused :not-supported})

      (.-disableRemotePlayback element)
      (js/Promise.resolve {:refused :disabled})

      :else
      (-> (js/Promise. (fn [resolve _] (resolve (.prompt remote))))
          (.then (fn [_] (select-keys (state element) [:state]))
                 (fn [e]
                   ;; §5.2.1 names the exceptions of prompt() in a way of
                   ;; its own, e.g. NotAllowedError for a closed picker
                   (let [{:keys [refused]} (media/refusal e)]
                     {:refused (get {:invalid-access :not-allowed
                                     :not-allowed    :dismissed
                                     :not-found      :no-devices
                                     :invalid-state  :disabled}
                                    refused
                                    refused)})))))))

(defn disable!
  "Keep the media `element` off remote devices, which also takes it out of
  the browser's own controls and picker.

  The call removes the element's x-webkit-airplay attribute too, since on
  an iPhone its value \"allow\" lets a video use AirPlay all the same."
  [^js element]
  (.setAttribute element "disableremoteplayback" "")
  (.removeAttribute element "x-webkit-airplay")
  nil)
