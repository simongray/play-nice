(ns dk.simongray.play-nice.page.tabs-test
  "One playback across tabs, where there are no Web Locks, where the window
  fires no events, and between two stand-ins for tabs, whose channels
  reach each other as a BroadcastChannel does."
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.play-nice.page.tabs :as tabs]
            [dk.simongray.play-nice.test-support :refer [run]]))

(deftest without-locks
  (is (nil? ((tabs/attach! (js/EventTarget.) "app" {:window #js {}})))))

(deftest without-window-events
  (let [requests  (atom [])
        locks     #js {:request (fn [name _ _]
                                  (swap! requests conj name)
                                  (js/Promise. (fn [_ _])))}
        ^js audio (doto (js/EventTarget.) (unchecked-set "paused" true))
        detach!   (tabs/attach! audio "app" {:window #js {:navigator #js {:locks locks}}})]
    (.dispatchEvent audio (js/Event. "play"))
    (is (= ["app"] @requests)
        "the lock at play, where the window, like Node's global object, has Web Locks and no events")
    (is (nil? (detach!)))))

(deftest the-newest-playback
  (let [now 100000]
    (is (= "b" (:id (tabs/newest [{:id "a" :paused true :changed 5}
                                  {:id "b" :paused false :at now :changed 1}]
                                 now)))
        "one that plays")
    (is (= "a" (:id (tabs/newest [{:id "a" :paused true :changed 5}
                                  {:id "b" :paused true :changed 1}]
                                 now)))
        "else the one that changed last")
    (is (= {:id "b" :paused true :gone true}
           (select-keys (tabs/newest [{:id "b" :paused false :at (- now 20000) :changed 1}] now)
                        [:id :paused :gone]))
        "and a playing one whose tab has gone quiet counts as paused")
    (is (nil? (tabs/newest [] now)))))

(deftest where-a-playback-is
  (let [s {:current-time 100 :at 0 :paused false :status :ready :playback-rate 2 :duration 600}]
    (is (= 120 (tabs/position s 10000)) "moved on by the time since, at its rate")
    (is (= 600 (tabs/position s 1000000)) "no further than the duration")
    (is (= 100 (tabs/position (assoc s :paused true) 10000)) "still while it's paused")
    (is (= 100 (tabs/position (assoc s :status :buffering) 10000)) "or waits for data")))

(defn channel-class
  "A stand-in for BroadcastChannel, whose channels deliver a message to the
  other channels of their name made from the same class, a task later, as
  those of a browser do."
  []
  (let [all (atom #{})]
    (fn [name]
      (this-as ^js this
        (let [listeners (atom [])]
          (set! (.-channelName this) name)
          (set! (.-addEventListener this) (fn [_ f] (swap! listeners conj f)))
          (set! (.-deliver this) (fn [data] (doseq [f @listeners] (f #js {:data data}))))
          (set! (.-postMessage this)
                (fn [data]
                  (doseq [^js other @all
                          :when (and (not (identical? other this))
                                     (= name (.-channelName other)))]
                    (js/setTimeout #(.deliver other data) 0))))
          (set! (.-close this) #(swap! all disj this))
          (swap! all conj this)
          this)))))

(defn window
  "A stand-in for the window of a tab, whose BroadcastChannel is the class
  `channels`, with no Web Locks and no events."
  [channels]
  #js {:BroadcastChannel channels})

(def src
  "The file that the stand-ins for media elements play."
  "https://example.org/1.mp3")

(defn element
  "A stand-in for a media element that has loaded `src`, paused at `t`
  seconds of 600, whose play and pause dispatch their events."
  [t]
  (let [^js el (js/EventTarget.)]
    (doseq [[k v] {"paused"       true
                   "ended"        false
                   "seeking"      false
                   "readyState"   4
                   "networkState" 1
                   "currentTime"  t
                   "duration"     600
                   "playbackRate" 1
                   "muted"        false
                   "volume"       1
                   "buffered"     #js {:length 0}
                   "seekable"     #js {:length 0}
                   "played"       #js {:length 0}
                   "error"        nil
                   "currentSrc"   src}]
      (unchecked-set el k v))
    (set! (.-play el) (fn []
                        (set! (.-paused el) false)
                        (.dispatchEvent el (js/Event. "play"))))
    (set! (.-pause el) (fn []
                         (when-not (.-paused el)
                           (set! (.-paused el) true)
                           (.dispatchEvent el (js/Event. "pause")))))
    el))

;; An answer takes two tasks of the stand-in's channels, its question's and
;; its own, so the promise waits two tasks after its time, which come after
;; any task that was due before them, even when the page was held up, e.g.
;; by Firefox's first read of localStorage
(defn settle
  "A promise that resolves once the messages before it have arrived."
  []
  (js/Promise. (fn [resolve _]
                 (js/setTimeout #(js/setTimeout (fn [] (js/setTimeout resolve 0)) 0) 10))))

(defn stop-all!
  "Call each of the functions in the atom `stops`, which detach and stop."
  [stops]
  (run! #(%) @stops))

(deftest one-playback-across-two-tabs
  (let [channels (channel-class)
        tab-a    (window channels)
        tab-b    (window channels)
        ^js a    (element 100)
        ^js b    (element 10)
        seen     (atom [])
        pressed  (atom [])
        stops    (atom [])]
    (run #(swap! stops conj (tabs/attach! a "app" {:window   tab-a
                                                   :handlers {:pause (fn [details]
                                                                       (swap! pressed conj details)
                                                                       (.pause a))}}))
         #(swap! stops conj (tabs/attach! b "app" {:window tab-b :align? true}))
         #(swap! stops conj (tabs/watch! "app" (partial swap! seen conj) {:window tab-b}))
         settle
         #(is (nil? (last @seen)) "nothing before a tab has played, though both have loaded")
         #(.play a)
         settle
         #(is (= [src false nil 100] ((juxt :id :paused :here? :current-time) (last @seen)))
              "the other tab learns what plays, and where")
         #(tabs/press! "app" {:action :pause} {:window tab-b})
         settle
         #(is (= [{:action :pause}] @pressed) "an action of the other tab runs where it plays")
         #(is (true? (:paused (last @seen))) "and the other tab learns of the pause")
         #(.play b)
         #(is (= 100 (.-currentTime b)) "a tab that plays starts from where the playback is")
         settle
         #(is (true? (:here? (last @seen))) "and has the newest playback now")
         #(stop-all! stops))))

(deftest a-change-of-a-paused-element
  (let [channels (channel-class)
        tab-a    (window channels)
        tab-b    (window channels)
        ^js a    (element 100)
        ^js b    (element 10)
        seen     (atom [])
        stops    (atom [])]
    (run #(swap! stops conj (tabs/attach! a "app" {:window tab-a}))
         #(swap! stops conj (tabs/attach! b "app" {:window tab-b}))
         #(swap! stops conj (tabs/watch! "app" (partial swap! seen conj) {:window tab-a}))
         #(do (.play a) (.pause a))
         settle
         #(do (set! (.-currentTime b) 30)
              (.dispatchEvent b (js/Event. "seeked")))
         settle
         #(is (true? (:here? (last @seen)))
              "a seek of a paused element isn't told, since it may be the one that loads an episode")
         #(do (set! (.-currentTime b) 40)
              (tabs/share! b))
         settle
         #(is (= [40 nil] ((juxt :current-time :here?) (last @seen)))
              "but one that share! tells is, and it's the newest")
         #(stop-all! stops))))

(deftest a-tab-that-opens-later
  (let [channels (channel-class)
        ^js a    (element 100)
        seen     (atom [])
        stops    (atom [])]
    (run #(swap! stops conj (tabs/attach! a "app" {:window (window channels)}))
         #(do (.play a) (.pause a))
         #(swap! stops conj (tabs/watch! "app" (partial swap! seen conj) {:window (window channels)}))
         settle
         #(is (= [true 100] ((juxt :paused :current-time) (last @seen)))
              "asks, and learns where the playback is")
         #(stop-all! stops))))
