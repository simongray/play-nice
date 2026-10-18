(ns dk.simongray.play-nice.page.tabs
  "One playback across the tabs of an app, through Web Locks and a
  BroadcastChannel.

  On a desktop, no engine stops the audio of one tab when another tab
  starts, so two tabs of a podcast app play at once and race to save the
  position. attach! holds a lock named for the app while the element
  plays, and takes it from any other tab that holds it, which then pauses
  its own element:

      (def detach!
        (attach! audio \"my-podcast-app\"))

  It also tells the other tabs what the element plays, where it is and
  whether it plays, so that the player of every tab can show the same
  playback, and it runs the actions that they send while it plays:

  - watch! gives a tab the newest playback across the tabs
  - position gives where a playback is by now
  - press! sends an action to the tab that plays
  - share! tells the other tabs of a change of a paused element
  - align! moves a paused element to the newest playback

  The functions take these opts:

  - :window, the window whose navigator holds the locks, and whose
    BroadcastChannel reaches the other tabs, instead of the page's, e.g. a
    stand-in in a test"
  (:require [cljs.reader :as reader]
            [dk.simongray.play-nice.media :as media]))

(def heartbeat-ms
  "How many milliseconds pass between two messages of a playing element to
  the other tabs, by which they also notice that its tab has gone."
  5000)

(def shared-keys
  "The keys of the state of an element that the other tabs learn, from
  media/state."
  [:paused :ended :current-time :duration :playback-rate :volume :muted :status])

(defonce ^{:doc "The shared playback of each name in each window, by the
  window and then the name, each an atom of its :channel, the :tab id of
  this page, its :own state, the states of the :others by their tab, the
  :watchers, the :handlers and playing? of the element of attach!, and how
  many calls :use it."}
  pages
  (js/WeakMap.))

(defonce ^{:doc "The functions of attach! for each element, by the
  element: :share!, which tells the other tabs of a change, and :align!,
  which moves it to the newest playback."}
  attached
  (js/WeakMap.))

(defn- page-of
  "The shared playback of `name` in `window`, made at its first use, or nil
  where the window has no BroadcastChannel."
  [^js window name]
  (when-let [channel-class (some-> window .-BroadcastChannel)]
    (let [named (or (.get pages window)
                    (let [named (atom {})]
                      (.set pages window named)
                      named))]
      (or (get @named name)
          (let [^js channel (new channel-class (str "dk.simongray.play-nice.page.tabs/" name))
                ;; in Node, a channel would keep the process running
                _           (some-> channel .-unref (.call channel))
                page        (atom {:channel channel
                                   :tab     (str (random-uuid))
                                   :others  {}
                                   :uses    0})]
            (swap! named assoc name page)
            page)))))

(defn- post!
  "Send the `message` to the other tabs of the `page`, from this one."
  [page message]
  (let [{:keys [^js channel tab]} @page]
    (.postMessage channel (pr-str (assoc message :tab tab)))))

;; The heartbeat that stops tells of a tab that went without a word, e.g.
;; one that crashed: its playback counts as paused where it was.
(defn newest
  "The newest of the playback `states` of the tabs at `now`, in
  milliseconds since the epoch: one that plays, and else the one that
  changed last. A playing one that hasn't told where it is for three
  heartbeats counts as paused, and :gone."
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
                    moving? (+ (* (or playback-rate 1) (max 0 (/ (- now at) 1000)))))]
      (if (and (number? duration) (js/isFinite duration))
        (min t duration)
        t))))

(defn- notify!
  "Call the watchers of the `page` with its newest playback."
  [page]
  (let [{:keys [own others watchers]} @page
        states (cond-> (vals others) own (conj (assoc own :here? true)))
        n      (newest states (js/Date.now))]
    (doseq [f (vals watchers)]
      (f n))))

(defn- received!
  "Take the message `data` that another tab sent to the `page`."
  [page data]
  (let [{:keys [tab] :as message} (try (reader/read-string data) (catch :default _ nil))
        {own-tab :tab :keys [answer! handlers playing?]} @page]
    (when (and (map? message) (not= tab own-tab))
      (case (:type message)
        :state (do (swap! page assoc-in [:others tab] (:state message))
                   (notify! page))
        :ask   (some-> answer! (apply []))
        :press (let [{:keys [action] :as details} (:details message)]
                 (when-let [f (and playing? (playing?) (get handlers action))]
                   (f details)))
        nil))))

(defn- used!
  "Start using the `page`, which follows its messages from the first use."
  [page]
  (when (= 1 (:uses (swap! page update :uses inc)))
    (.addEventListener ^js (:channel @page) "message"
                       (fn [^js e] (received! page (.-data e))))))

(defn- unused!
  "Stop using the `page` of `name` in `window`, which closes its channel at
  the last use."
  [^js window name page]
  (when (zero? (:uses (swap! page update :uses dec)))
    (.close ^js (:channel @page))
    (some-> (.get pages window) (swap! dissoc name))))

(defn- newer-elsewhere
  "The newest playback that another tab of the `page` shared of `id`,
  changed after `changed`, or nil."
  [page id changed]
  (let [{:keys [others]} @page
        n                (newest (vals others) (js/Date.now))]
    (when (and n (= id (:id n)) (> (or (:changed n) 0) (or changed 0)))
      n)))

;; W3C Web Locks API, request() with steal
(defn attach!
  "Hold the Web Lock of `name` while the media `element` plays, pause the
  element when another tab takes the lock, and tell the other tabs of its
  playback, with the `opts` above and these. Return a function that
  detaches it.

  - :id-fn, a function of no arguments that gives what the element plays,
    as data that EDN prints, e.g. the id of an episode, by default its
    currentSrc
  - :handlers, the handlers of the actions that the other tabs send with
    press! while the element plays, a map of action to a function of the
    details, as for controls/listen!, e.g. {:pause #(.pause audio)}
  - :align?, true to start from the newest playback of what the element
    plays, as another tab shared it, when the element plays

  The element takes the lock from any holder when it plays, and lets it
  go when it pauses and at pagehide, since a held lock keeps a page out of
  the back/forward cache. It tells the other tabs of its playback when it
  plays and pauses, every heartbeat-ms while it plays, and when another
  tab asks. A change of a paused element, e.g. a seek, isn't told, since
  attach! can't tell the listener's seek from the one that loads an
  episode, so call share! after one. Where the browser has no Web Locks,
  the element holds no lock, and where it has no BroadcastChannel, the
  other tabs learn nothing. Where the window fires no events, e.g. Node's
  global object, which has Web Locks from Node 24.5, the element still
  takes the lock as it plays and lets go of it as it pauses."
  ([element name]
   (attach! element name {}))
  ([^js element name {:keys [handlers align?] :as opts}]
   (let [^js window    (:window opts js/globalThis)
         ^js navigator (some-> window .-navigator)
         ^js locks     (some-> navigator .-locks)
         locks?        (fn? (some-> locks .-request))
         page          (page-of window name)
         id-fn         (:id-fn opts #(.-currentSrc element))
         ;; when the listener last changed the playback here, in ms
         changed       (atom nil)
         heartbeat     (atom nil)
         ;; the lock that the element wants, by a token of its own, so
         ;; that a lock it lets go of and takes again isn't stolen
         wanted        (atom nil)
         snapshot      (fn [overrides]
                         (-> (media/state element)
                             (select-keys shared-keys)
                             (assoc :id (id-fn) :at (js/Date.now) :changed @changed)
                             (merge overrides)))
         share!        (fn [overrides]
                         (when (and page @changed)
                           (let [s (snapshot overrides)]
                             (swap! page assoc :own s)
                             (post! page {:type :state :state s})
                             (notify! page))))
         align!        (fn []
                         (when-let [n (and page (newer-elsewhere page (id-fn) @changed))]
                           (when-let [t (position n (js/Date.now))]
                             (set! (.-currentTime element) t))
                           (when (number? (:playback-rate n))
                             (set! (.-playbackRate element) (:playback-rate n)))
                           true))
         beat!         (fn []
                         (some-> @heartbeat js/clearInterval)
                         (reset! heartbeat (js/setInterval #(share! {}) heartbeat-ms)))
         unbeat!       (fn []
                         (some-> @heartbeat js/clearInterval)
                         (reset! heartbeat nil))
         hold!         (fn []
                         (when (and locks? (not @wanted))
                           (let [token   #js {}
                                 current #(identical? token (:token @wanted))
                                 ;; held until let-go! resolves the promise
                                 granted (fn [_]
                                           (js/Promise.
                                            (fn [resolve _]
                                              (if (current)
                                                (swap! wanted assoc :release resolve)
                                                (resolve nil)))))
                                 ;; an AbortError, where another tab took it
                                 stolen  (fn [_]
                                           (when (current)
                                             (reset! wanted nil)
                                             (.pause element)))]
                             (reset! wanted {:token token})
                             (.catch (.request locks name #js {:steal true} granted)
                                     stolen))))
         let-go!       (fn []
                         (when-let [{:keys [release]} @wanted]
                           (reset! wanted nil)
                           (when release
                             (release nil))))
         played!       (fn [_]
                         (when align?
                           (align!))
                         (reset! changed (js/Date.now))
                         (hold!)
                         (share! {})
                         (beat!))
         paused!       (fn [_]
                         (reset! changed (js/Date.now))
                         (unbeat!)
                         (let-go!)
                         (share! {}))
         moved!        (fn [_]
                         (when-not (.-paused element)
                           (share! {})))
         hidden!       (fn [_]
                         (unbeat!)
                         (let-go!)
                         (share! {:paused true}))
         shown!        (fn [^js e]
                         (when (and (.-persisted e) (not (.-paused element)))
                           (hold!)
                           (share! {})
                           (beat!)))
         paged?        (fn? (.-addEventListener window))]
     (.addEventListener element "play" played!)
     (.addEventListener element "pause" paused!)
     (.addEventListener element "seeked" moved!)
     (.addEventListener element "ratechange" moved!)
     (.addEventListener element "ended" moved!)
     (when paged?
       (.addEventListener window "pagehide" hidden!)
       (.addEventListener window "pageshow" shown!))
     (.set attached element {:share! #(do (reset! changed (js/Date.now))
                                          (share! {}))
                             :align! align!})
     (when page
       (used! page)
       (swap! page assoc
              :handlers handlers
              :playing? #(not (.-paused element))
              :answer!  #(share! {}))
       (post! page {:type :ask}))
     (when-not (.-paused element)
       (played! nil))
     (fn []
       (.removeEventListener element "play" played!)
       (.removeEventListener element "pause" paused!)
       (.removeEventListener element "seeked" moved!)
       (.removeEventListener element "ratechange" moved!)
       (.removeEventListener element "ended" moved!)
       (when paged?
         (.removeEventListener window "pagehide" hidden!)
         (.removeEventListener window "pageshow" shown!))
       (.delete attached element)
       (unbeat!)
       (let-go!)
       (when page
         (swap! page dissoc :handlers :playing? :answer!)
         (unused! window name page))
       nil))))

(defn share!
  "Tell the other tabs of a change that the listener made to the attached
  media `element` while it's paused, e.g. a seek, which makes its
  playback the newest."
  [element]
  (some-> (.get attached element) :share! (apply [])))

(defn align!
  "Move the attached media `element`, which is paused, to the newest
  playback of what it plays, when another tab shared one after the last
  change here: its position by now, and its rate. Return whether it
  moved, e.g. before a skip from where the playback is."
  [element]
  (boolean (some-> (.get attached element) :align! (apply []))))

(defn watch!
  "Call `f` with the newest playback of `name` across the tabs, now and
  whenever it changes, with the `opts` above, and return a function that
  stops it.

  The playback is a map of the keys of shared-keys from media/state, with
  the :id of what it plays, as the :id-fn of attach! gives it, :at, the
  time in milliseconds at which its :current-time was taken, for
  position, and :changed, when the listener last changed it. It's :here?
  when it's the one of this tab's element, and nil before any tab has
  played. Where the window has no BroadcastChannel, `f` gets nothing."
  ([name f]
   (watch! name f {}))
  ([name f opts]
   (let [^js window (:window opts js/globalThis)]
     (if-let [page (page-of window name)]
       (let [k     (random-uuid)
             given (atom ::none)
             g     (fn [n]
                     (when (not= n @given)
                       (reset! given n)
                       (f n)))
             ;; to notice a tab that has gone, whose state no message changes
             timer (js/setInterval #(notify! page) heartbeat-ms)]
         (used! page)
         (swap! page assoc-in [:watchers k] g)
         (post! page {:type :ask})
         (notify! page)
         (fn []
           (js/clearInterval timer)
           (swap! page update :watchers dissoc k)
           (unused! window name page)
           nil))
       (constantly nil)))))

(defn press!
  "Send the action of `details`, e.g. {:action :pause}, to the tab whose
  element plays the playback of `name`, with the `opts` above, which runs
  it through the :handlers of its attach!. Where this page's own element
  plays, it runs here. It needs an attach! or a watch! of `name` in this
  page, and does nothing where no element plays."
  ([name details]
   (press! name details {}))
  ([name details opts]
   (let [^js window (:window opts js/globalThis)]
     (when-let [page (some-> (.get pages window) deref (get name))]
       (let [{:keys [playing? handlers]} @page]
         (if (and playing? (playing?))
           (some-> (get handlers (:action details)) (apply [details]))
           (post! page {:type :press :details details})))))))
