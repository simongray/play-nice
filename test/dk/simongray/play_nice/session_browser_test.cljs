(ns dk.simongray.play-nice.session-browser-test
  "The Media Session in a browser: the page's own session, and a stand-in
  that records each call, with a media element that plays."
  (:require [clojure.set :as set]
            [clojure.test :refer [deftest is]]
            [dk.simongray.play-nice.keys :as keys]
            [dk.simongray.play-nice.session :as session]
            [dk.simongray.play-nice.test-support
             :refer [garbage muted-video next-event run silence]]))

(defn recorder
  "A stand-in for a MediaSession that adds each call to the atom `calls`,
  with the `opts` below. Like an engine of its own, it throws a TypeError
  for what it refuses.

  - :refused, the names of the actions it refuses
  - :refuse-pred, a predicate of the position states it refuses
  - :handlers, an atom that it keeps the handlers in, by action"
  [calls {:keys [refused refuse-pred handlers]
          :or   {refused     #{}
                 refuse-pred (constantly false)
                 handlers    (atom {})}}]
  (let [note!    #(swap! calls conj %)
        metadata (atom nil)
        ^js s    #js {:setActionHandler
                      (fn [action f]
                        (when (contains? refused action)
                          (throw (js/TypeError. action)))
                        (swap! handlers assoc action f)
                        (note! [:handler action (fn? f)]))
                      :setPositionState
                      (fn
                        ([]
                         (note! [:position nil]))
                        ([state]
                         (let [m (keys/->clj state)]
                           (when (refuse-pred m)
                             (throw (js/TypeError. "refused")))
                           (note! [:position m]))))}]
    (js/Object.defineProperty s "playbackState"
                              #js {:get (constantly "none")
                                   :set #(note! [:playback-state %])})
    (js/Object.defineProperty s "metadata"
                              #js {:get #(deref metadata)
                                   :set (fn [^js m]
                                          (reset! metadata m)
                                          (note! [:metadata
                                                  (some-> m .-title)]))})
    s))

(deftest handlers-on-a-stand-in
  (let [calls    (atom [])
        handlers (atom {})
        opts     {:session (recorder calls {:refused  #{"voiceactivity"}
                                            :handlers handlers})}
        details  (atom nil)]
    (is (= #{:voiceactivity}
           (session/actions! {:play          identity
                              :seekto        #(reset! details %)
                              :voiceactivity identity}
                             opts))
        "an action that the browser refuses")
    ((get @handlers "seekto")
     #js {"action" "seekto" "seekTime" 12 "fastSeek" true})
    (is (= {:action :seekto :seek-time 12 :fast-seek true} @details)
        "the details as a map")
    (reset! calls [])
    (is (= #{} (session/actions! {:play identity} opts)))
    (is (= #{[:handler "play" true] [:handler "seekto" false]} (set @calls))
        "clearing the action that the call before set")
    (reset! calls [])
    (is (= (disj session/actions :voiceactivity)
           (:actions (session/support opts))))
    (is (not-any? #{[:handler "play" false]} @calls)
        "support leaves the library's handlers alone")))

(deftest the-position-on-a-stand-in
  (let [calls   (atom [])
        ;; as WebKit's IDL has it, a required and finite duration
        finite? (comp session/finite? :duration)
        safari  {:session (recorder calls {:refuse-pred (complement finite?)})}]
    (is (= {:duration 60 :playback-rate 1.0 :position 10}
           (session/position! {:duration 60 :position 10} safari)))
    (is (nil? (session/position! nil safari)))
    (is (nil? (session/position! {:duration ##Inf :position 5} safari))
        "a live stream, which Safari refuses")
    (is (nil? (session/position! {:position 5} safari)) "no duration")
    (is (= [[:position {:duration 60 :playback-rate 1 :position 10}]
            [:position nil]
            [:position nil]
            [:position nil]]
           @calls)
        "each clears without an argument, since Safari throws for {}")))

(deftest metadata-on-a-stand-in
  (let [calls (atom [])
        opts  {:session (recorder calls {})}
        m     {:title "Episode 3" :artist "John Doe"}]
    (is (= m (session/metadata! m opts)))
    (is (= m (session/metadata! m opts)))
    (is (= [[:metadata "Episode 3"]] @calls)
        "the same metadata twice changes nothing")
    (is (= {:title "Talk" :artwork [{:src "cover.png"}]}
           (session/metadata! {:title   "Talk"
                               :artwork [{:src "http://[bad"}
                                         {:src "cover.png"}]}
                              opts))
        "without an image whose URL doesn't parse")
    (is (nil? (session/metadata! nil opts)))
    (is (= [[:metadata "Episode 3"] [:metadata "Talk"] [:metadata nil]]
           @calls))))

(deftest following-an-element
  (let [calls   (atom [])
        opts    {:session     (recorder calls {})
                 :position-fn session/element-position}
        ^js a   (muted-video)
        detach! (atom nil)
        last-of (fn [kind] (last (filter #(= kind (first %)) @calls)))]
    (set! (.-src a) (silence))
    (run #(reset! detach! (session/attach! a opts))
         #(is (= [[:playback-state "paused"] [:position nil]] @calls)
              "no position before the metadata")
         #(when-not (pos? (.-readyState a)) (next-event a "loadedmetadata"))
         #(is (= [:position {:duration 1 :playback-rate 1 :position 0}]
                 (last-of :position)))
         ;; a refusal fails the test rather than leave it waiting
         #(let [playing (next-event a "playing")]
            (.then (.play a) (fn [_] playing)))
         #(is (= [:playback-state "playing"] (last-of :playback-state)))
         #(let [paused (next-event a "pause")] (.pause a) paused)
         #(is (= [:playback-state "paused"] (last-of :playback-state)))
         #(let [seeked (next-event a "seeked")]
            (set! (.-currentTime a) 0.5)
            seeked)
         ;; WebKit puts it a little past, e.g. at 0.50005, or 0.511 on iOS
         #(is (< (abs (- 0.5 (:position (second (last-of :position))))) 0.02)
              "the position after a seek")
         #(let [changed (next-event a "ratechange")]
            (set! (.-playbackRate a) 2)
            changed)
         #(is (= 2 (:playback-rate (second (last-of :position)))))
         #(do (reset! calls [])
              (@detach!)
              (.dispatchEvent a (js/Event. "pause"))
              (is (= [[:playback-state "none"] [:position nil]] @calls)
                  "after detaching, the element's events change nothing")))))

(deftest one-element-at-a-time
  (let [calls  (atom [])
        opts   {:session (recorder calls {})}
        ^js a  (js/document.createElement "audio")
        ^js b  (js/document.createElement "audio")
        detach (session/attach! a opts)]
    (reset! calls [])
    (session/attach! b opts)
    (is (= [[:playback-state "none"] [:position nil]
            [:playback-state "paused"] [:position nil]]
           @calls)
        "attaching another detaches the first, and no :position-fn clears it")
    (reset! calls [])
    (set! (.-playbackRate a) 2)
    (.dispatchEvent a (js/Event. "ratechange"))
    (is (= [] @calls) "the first element's changes no longer reach it")
    (set! (.-playbackRate b) 2)
    (.dispatchEvent b (js/Event. "ratechange"))
    (is (= [[:playback-state "paused"] [:position nil]] @calls)
        "while the second's do")
    (reset! calls [])
    (detach)
    (is (= [] @calls) "the first detach no longer touches the session")))

(deftest an-element-that-failed
  (let [calls  (atom [])
        opts   {:session (recorder calls {})}
        ^js el (js/EventTarget.)]
    (set! (.-paused el) false)
    (session/attach! el opts)
    (is (= [[:playback-state "playing"] [:position nil]] @calls))
    ;; a fatal error mid-stream fires error alone, and leaves paused false
    (set! (.-error el) #js {:code 2})
    (.dispatchEvent el (js/Event. "error"))
    (is (= [:playback-state "paused"] (nth @calls 2))
        "paused, rather than playing on")))

(deftest a-file-that-fails
  (let [calls   (atom [])
        ^js v   (muted-video)
        detach! (session/attach! v {:session (recorder calls {})})]
    ;; Firefox rejects the play promise before the error event fires
    (run #(let [failed (next-event v "error")]
            (set! (.-src v) (garbage))
            (.catch (.play v) (constantly nil))
            failed)
         #(is (= [:playback-state "paused"]
                 (last (filter (comp #{:playback-state} first) @calls))))
         detach!)))

(deftest the-session-of-the-page
  (if-let [^js s (session/media-session {})]
    (let [m            (session/metadata!
                        {:title        "Episode 3"
                         :artist       "John Doe"
                         :artwork      [{:src "http://[bad"}
                                        {:src "cover.png" :sizes "512x512"}]
                         :chapter-info [{:title "Intro" :start-time 0}
                                        {:title "Talk" :start-time 94}]})
          ^js metadata (.-metadata s)]
      (is (= "Episode 3" (.-title metadata)))
      (is (= [{:src "cover.png" :sizes "512x512"}] (:artwork m)))
      (is (.endsWith (.-src (aget (.-artwork metadata) 0)) "/cover.png")
          "resolved against the page")
      (when (:chapter-info? (session/support))
        (is (= ["Intro" "Talk"]
               (mapv #(.-title ^js %) (.-chapterInfo metadata)))))
      (let [refused (session/actions! {:play          identity
                                       :seekto        identity
                                       :voiceactivity identity})]
        (is (set/subset? refused #{:voiceactivity}))
        (is (empty? (set/intersection refused
                                      (:actions (session/support))))))
      (is (nil? (run! session/position! [{:duration ##Inf :position 5}
                                         {:duration 60 :position 100}
                                         {:duration      60
                                          :position      5
                                          :playback-rate 0}
                                         {:duration 60 :position ##NaN}
                                         {:duration -1}
                                         {}
                                         nil]))
          "no position state throws")
      (session/actions! {})
      (session/position! nil)
      (session/metadata! nil)
      (is (nil? (.-metadata s)) "nil clears the metadata"))
    (is (nil? (session/support)) "nothing where the browser has none")))
