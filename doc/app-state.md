# How to keep the lock screen in step with your app's state

Attach the media element once, and call `session/metadata!` and
`session/actions!` whenever your app's state changes. You can call both
as often as you like. Setting the same metadata twice in a row does
nothing, and `actions!` replaces the handlers it set before with those
it's given.

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

The track buttons only change the state, so you write the code that
plays a track when `:playing` changes.

## With Replicant

With [Replicant](https://github.com/cjohansen/replicant), call `show!`
in the watch that renders the app, and load and follow the element when
Replicant mounts it. A life-cycle hook given as data, e.g.
`[:player/mounted src]`, reaches your dispatch function, which gets the
element as `:replicant/node`:

```clojure
(require '[dk.simongray.play-nice.media :as media]
         '[replicant.dom :as r])

(defonce detach!
  (atom (constantly nil)))

(defn mount!
  "Load the track at `src` into the media `element`, keep its state in the
  app, and put it on the lock screen."
  [element src]
  (media/source! element {:src src})
  (reset! detach! (juxt (media/watch! element #(swap! app assoc :player %))
                        (session/attach! element))))

(r/set-dispatch!
 (fn [{:replicant/keys [node]} [action src]]
   (case action
     ;; after the render, since the swap of watch! renders again
     :player/mounted   (js/queueMicrotask #(mount! node src))
     :player/unmounted (@detach!))))

[:audio {:replicant/key        src
         :replicant/on-mount   [:player/mounted src]
         :replicant/on-unmount [:player/unmounted]}]
```

Replicant calls the hooks inside its render. Since `watch!` calls its
function at once, its swap would render the app again in the middle of
that render. Replicant then puts the second render off to the next
frame, and warns "Triggered a render while rendering" in development. So
the dispatch mounts the element in a microtask, after the render. The
`:replicant/key` gives each track an element of its own, so that a new
track mounts anew.

## With re-frame

With [re-frame](https://day8.github.io/re-frame/), keep the media
element in an atom rather than in the app's database, since an element
isn't data. Then make what you do to it effects. The `:media` effect
below applies effects of `media/apply!`. Where you name an event in
`:on-answers`, it dispatches their answers to it, e.g. to show that the
browser refused to play:

```clojure
(require '[dk.simongray.play-nice.media :as media])

(defonce audio
  (atom nil))

(rf/reg-fx :media
  (fn [{:keys [effects on-answers]}]
    (cond-> (media/apply! @audio effects)
      on-answers (.then #(rf/dispatch (conj on-answers %))))))

(rf/reg-event-fx :player/play-clicked
  (fn [_ _]
    {:fx [[:media {:effects    [[:play {:muted-fallback true}]]
                   :on-answers [:player/answered]}]]}))

(rf/reg-event-db :player/answered
  (fn [db [_ [answer]]]
    (assoc db :answer answer)))
```

Dispatch the click's event with `dispatch-sync`, e.g.
`[:button {:on-click #(rf/dispatch-sync [:player/play-clicked])} "Play"]`,
so that the browser gets the play while the click still lets the page
play.

Make the lock screen an effect too, and return it from the events that
change what plays. Its buttons dispatch events, and
`session/action-effects` turns a press into effects. The `:press` effect
reads the element's state when it runs, rather than from the database,
where the state is only as new as the last event that stored it:

```clojure
(rf/reg-fx :lock-screen
  (fn [{:keys [metadata handlers]}]
    (session/metadata! metadata)
    (session/actions! handlers)))

(def skips
  "The seconds of the skip buttons."
  {:seekbackward 15 :seekforward 30})

(rf/reg-fx :press
  (fn [details]
    (media/apply! @audio (session/action-effects details
                                                 (media/state @audio)
                                                 skips))))

(def handlers
  "The buttons of the lock screen, as events. Keep :seekto, since
  Safari's lock screen has no scrubber without it."
  (assoc (zipmap [:play :pause :stop :seekto :seekbackward :seekforward]
                 (repeat #(rf/dispatch [:player/press %])))
         :nexttrack #(rf/dispatch [:player/next])))

(rf/reg-event-fx :player/press
  (fn [_ [_ details]]
    {:fx [[:press details]]}))

(rf/reg-event-fx :player/play
  (fn [{:keys [db]} [_ index]]
    (let [db (assoc db :playing index)]
      {:db db
       :fx [[:lock-screen {:metadata (now-playing db)
                           :handlers handlers}]]})))
```

Attach the element in a ref callback, which React calls with the
element when it mounts and with nil when it goes. In the callback, also
follow the element's state into the database. Define the callback once:
if it were a new function at each render, React would call it at every
render, and the element would be detached and attached each time:

```clojure
(defonce detach!
  (atom (constantly nil)))

(defn attach-audio!
  "Attach the media `element` that React mounts, or detach it at nil."
  [element]
  (if element
    (let [stop! (media/watch! element #(rf/dispatch [:player/state %]))]
      (reset! audio element)
      (reset! detach! (juxt stop! (session/attach! element))))
    (@detach!)))

[:audio {:ref attach-audio!}]
```
