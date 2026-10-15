(ns dk.simongray.play-nice.remote-playback-test
  "Remote Playback on stand-ins for a media element and its RemotePlayback."
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.play-nice.remote-playback :as remote]
            [dk.simongray.play-nice.test-support :refer [run]]))

(defn stand-in
  "A stand-in for a RemotePlayback that keeps its availability callback in
  the atom `callback` and the ids it cancels in the atom `cancelled`, and
  whose watchAvailability returns `watched` and prompt `prompted`, each a
  function of no arguments."
  [callback cancelled watched prompted]
  (doto (js/EventTarget.)
    (unchecked-set "state" "disconnected")
    (unchecked-set "watchAvailability" (fn [f] (reset! callback f) (watched)))
    (unchecked-set "cancelWatchAvailability" (fn [id]
                                               (swap! cancelled conj id)
                                               (js/Promise.resolve)))
    (unchecked-set "prompt" prompted)))

(defn element
  "A stand-in for a media element whose remote is `r`."
  [r]
  (doto (js/EventTarget.)
    (unchecked-set "remote" r)))

(defn refusing
  "A function that returns a promise rejected with the DOMException named
  `s`."
  [s]
  #(js/Promise.reject (js/DOMException. "" s)))

(defn tick
  "A promise that resolves once the promises of watch! have settled."
  []
  (js/Promise. (fn [resolve _] (js/setTimeout resolve 0))))

(deftest the-state
  (is (= {:availability :unavailable :state :disconnected} (remote/state #js {}))
      "without Remote Playback")
  (is (= {:availability :unknown :state :connecting}
         (remote/state #js {:remote #js {:state "connecting"}}))
      "and the availability, which only watch! learns")
  (is (= :connected (:state (remote/state #js {:remote #js {:state "disconnected"}
                                                :webkitCurrentPlaybackTargetIsWireless true})))
      "on AirPlay, as WebKit says"))

(deftest prompting
  (let [answer (fn [expected prompted & [disabled]]
                 #(let [^js el (element (stand-in (atom nil) (atom [])
                                                  (constantly (js/Promise.resolve 1))
                                                  prompted))]
                    (set! (.-disableRemotePlayback el) (boolean disabled))
                    (.then (remote/prompt! el) (fn [a] (is (= expected a))))))]
    (run (answer {:state :disconnected} (constantly (js/Promise.resolve)))
         (answer {:refused :not-allowed} (refusing "InvalidAccessError"))
         (answer {:refused :dismissed} (refusing "NotAllowedError"))
         (answer {:refused :no-devices} (refusing "NotFoundError"))
         (answer {:refused :operation} (refusing "OperationError"))
         (answer {:refused :disabled} (constantly (js/Promise.resolve)) true)
         #(.then (remote/prompt! #js {})
                 (fn [a] (is (= {:refused :not-supported} a)))))))

(deftest watching
  (let [states    (atom [])
        callback  (atom nil)
        cancelled (atom [])
        ^js r     (stand-in callback cancelled
                            (constantly (js/Promise.resolve 7))
                            (constantly (js/Promise.resolve)))
        ^js el    (element r)
        stop!     (remote/watch! el (partial swap! states conj))]
    (run #(@callback true)
         #(do (set! (.-state r) "connecting")
              (.dispatchEvent r (js/Event. "connecting")))
         #(.dispatchEvent el (doto (js/Event. "webkitplaybacktargetavailabilitychanged")
                               (unchecked-set "availability" "not-available")))
         #(do (set! (.-webkitCurrentPlaybackTargetIsWireless el) true)
              (.dispatchEvent el (js/Event. "webkitcurrentplaybacktargetiswirelesschanged")))
         #(is (= [{:availability :unknown :state :disconnected}
                  {:availability :available :state :disconnected}
                  {:availability :available :state :connecting}
                  {:availability :unavailable :state :connecting}
                  {:availability :unavailable :state :connected}]
                 @states))
         #(is (nil? (stop!)))
         #(is (= [7] @cancelled) "the watch of the availability too")
         #(do (@callback true)
              (is (= 5 (count @states)) "until it stops")))))

(deftest watching-where-the-browser-cannot-tell
  (let [states   (atom [])
        watch!   (fn [watched]
                   (remote/watch! (element (stand-in (atom nil) (atom []) watched
                                                     (constantly nil)))
                                  (partial swap! states conj)))]
    (watch! (refusing "NotSupportedError"))
    (watch! (refusing "InvalidStateError"))
    (remote/watch! #js {:addEventListener    (constantly nil)
                        :removeEventListener (constantly nil)}
                   (partial swap! states conj))
    (run tick
         ;; each starts as :unknown, but without Remote Playback, and then
         ;; only the disabled one changes
         #(is (= [:unknown :unknown :unavailable :unavailable]
                 (map :availability @states))
              "only in the picker, disabled, and without Remote Playback"))))

(deftest stopping-early
  (let [cancelled (atom [])
        resolve!  (atom nil)
        stop!     (remote/watch! (element (stand-in (atom nil) cancelled
                                                    #(js/Promise. (fn [resolve _]
                                                                    (reset! resolve! resolve)))
                                                    (constantly nil)))
                                 identity)]
    (stop!)
    (@resolve! 3)
    (run tick
         #(is (= [3] @cancelled)
              "the watch that the browser started after the stop"))))
