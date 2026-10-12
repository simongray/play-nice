# How to keep the lock screen in step with your app's state

Call `session/metadata!` and `session/actions!` whenever your app's state
changes, and attach the media element once. You can call both as often
as you like. The same metadata twice in a row does nothing, and
`actions!` sets exactly the handlers it's given, clearing those that it
set before.

## With an atom

Write the lock screen as two functions of the state, one for the metadata
and one for the buttons:

```clojure
(require '[dk.simongray.play-nice.session :as session])

(defonce app
  (atom {:tracks  [{:title  "So What"
                    :artist "Miles Davis"
                    :cover  "/covers/kind-of-blue.png"}
                   {:title  "Freddie Freeloader"
                    :artist "Miles Davis"
                    :cover  "/covers/kind-of-blue.png"}]
         :playing 0}))

(defn now-playing
  "The metadata of the track that plays in the app `state`, or nil."
  [{:keys [tracks playing] :as state}]
  (when-let [{:keys [title artist cover]} (get tracks playing)]
    {:title   title
     :artist  artist
     :artwork [{:src cover :sizes "512x512" :type "image/png"}]}))

(defn buttons
  "The handlers of the lock screen for the app `state` and the media
  `element`: those of the element, and the track buttons that lead
  somewhere."
  [{:keys [tracks playing] :as state} element]
  (cond-> (session/element-actions element {:seekbackward 10
                                            :seekforward  10})
    (< (inc playing) (count tracks))
    (assoc :nexttrack (fn [_] (swap! app update :playing inc)))

    (pos? playing)
    (assoc :previoustrack (fn [_] (swap! app update :playing dec)))))

(defn show!
  "Show the app `state` on the lock screen of the media `element`."
  [state element]
  (session/metadata! (now-playing state))
  (session/actions! (buttons state element)))
```

Then attach the element, and show the state at every change:

```clojure
(def audio
  (js/document.querySelector "audio"))

(session/attach! audio)
(add-watch app ::lock-screen (fn [_ _ _ state] (show! state audio)))
(show! @app audio)
```

The track buttons only change the state, so the code that plays a track
when `:playing` changes is your own. The first track has no previous
button, and the last has no next one.

## With Replicant

Call `show!` in the watch that renders, and attach the element when it
mounts:

```clojure
(defonce detach!
  (atom (constantly nil)))

[:audio {:replicant/on-mount   (fn [{:replicant/keys [node]}]
                                 (reset! detach! (session/attach! node)))
         :replicant/on-unmount (fn [_] (@detach!))}]
```

## With re-frame

Make the lock screen an effect, and return it from the events that change
what plays. The handlers dispatch events of their own, since the element
isn't in the app's database:

```clojure
(rf/reg-fx :lock-screen
  (fn [{:keys [metadata handlers]}]
    (session/metadata! metadata)
    (session/actions! handlers)))

(def handlers
  "The buttons of the lock screen, as events."
  {:play      #(rf/dispatch [:player/resume])
   :pause     #(rf/dispatch [:player/pause])
   :nexttrack #(rf/dispatch [:player/next])})

(rf/reg-event-fx :player/play
  (fn [{:keys [db]} [_ index]]
    (let [db (assoc db :playing index)]
      {:db db
       :fx [[:lock-screen {:metadata (now-playing db)
                           :handlers handlers}]]})))
```

Attach the element in a ref callback, which React calls with nil when the
element goes:

```clojure
[:audio {:ref #(if % (reset! detach! (session/attach! %)) (@detach!))}]
```
