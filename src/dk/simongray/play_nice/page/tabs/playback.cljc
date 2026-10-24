(ns dk.simongray.play-nice.page.tabs.playback
  "The playbacks that an app sends to its other tabs itself, e.g. over a
  BroadcastChannel: newest picks the newest of those that a tab received,
  and position moves one on to where it is by now.")

(def heartbeat-ms
  "How many milliseconds may pass between two playbacks that a tab sends
  while its element plays. newest counts the tab as gone after three."
  5000)

;; A tab that goes silent while its element plays, e.g. one that crashed,
;; leaves its playback paused where it was.
(defn newest
  "The newest of the playback `states` of the tabs at `now`, in
  milliseconds since the epoch: one that plays, and else the one that
  changed last. A playing one that hasn't told where it is for three
  heartbeats counts as paused, and :gone.

  Each state is a map of keys from media/state, with :at, the time in
  milliseconds at which its :current-time was taken, and :changed, when
  it last changed."
  [states now]
  (let [heard (for [{:keys [paused at] :as s} states
                    :when s]
                (if (and (false? paused) (number? at) (> (- now at) (* 3 heartbeat-ms)))
                  (assoc s :paused true :gone true)
                  s))]
    (or (first (filter #(false? (:paused %)) heard))
        (last (sort-by #(or (:changed %) 0) heard)))))

;; W3C Media Session, position state: a position moves on from the time it
;; was taken at its rate, and no further than the duration
;; https://w3c.github.io/mediasession/#position-state-sec
(defn position
  "Where the playback `state` is at `now`, in milliseconds since the epoch:
  its :current-time, moved on by the time since its :at at its rate while
  it plays, and no further than its duration."
  [{:keys [current-time at paused status playback-rate duration] :as state} now]
  (when (number? current-time)
    (let [moving? (and (false? paused) (= :ready status) (number? at))
          t       (cond-> current-time
                    moving? (+ (* (or playback-rate 1) (max 0 (/ (- now at) 1000.0)))))]
      (if (and (number? duration)
               #?(:clj  (Double/isFinite (double duration))
                  :cljs (js/Number.isFinite duration)))
        (min t duration)
        t))))
