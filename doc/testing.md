# How to test a player without the browser's session

Give every call the option `:session` with a stand-in for the browser's
MediaSession, which records what the calls set. Your tests then show what
your player puts on the lock screen, and run in Node too, except those of
`metadata!`, which needs a browser's MediaMetadata.

## A stand-in session

The stand-in is a JavaScript object with the two methods and the playback
state that play-nice uses. This one adds each call to an atom, and keeps
the handlers so that a test can press a button:

```clojure
(defn stand-in
  "A stand-in for a MediaSession that adds each call to the atom `calls`,
  and keeps the handlers in the atom `handlers`."
  [calls handlers]
  (let [note! #(swap! calls conj %)
        ^js s #js {:setActionHandler
                   (fn [action f]
                     (swap! handlers assoc action f)
                     (note! [:handler action (some? f)]))
                   :setPositionState
                   (fn
                     ([] (note! [:position nil]))
                     ([state] (note! [:position (js->clj state)])))}]
    (js/Object.defineProperty s "playbackState"
                              #js {:set #(note! [:playback-state %])})
    s))
```

## Test the buttons

Set the handlers with the stand-in, then press one as the browser would,
with the details as a JavaScript object:

```clojure
(deftest the-buttons
  (let [calls    (atom [])
        handlers (atom {})
        pressed  (atom nil)
        opts     {:session (stand-in calls handlers)}]
    (session/actions! {:play identity :seekto #(reset! pressed %)} opts)
    (is (= #{[:handler "play" true] [:handler "seekto" true]} (set @calls)))
    ((get @handlers "seekto") #js {"action" "seekto" "seekTime" 12})
    (is (= {:action :seekto :seek-time 12} @pressed))))
```

To test how your player copes with a browser that refuses an action,
make the stand-in throw a TypeError for it, as Firefox does for
`"togglemicrophone"`. `actions!` then returns that action.

## Test what follows an element

In a browser, attach a real media element. In Node, an `EventTarget` with
the element's attributes stands in for it, and `dispatchEvent` fires its
events:

```clojure
(deftest following-an-element
  (let [calls  (atom [])
        opts   {:session     (stand-in calls (atom {}))
                :position-fn session/element-position}
        ^js el (js/EventTarget.)]
    (set! (.-duration el) 60)
    (set! (.-currentTime el) 10)
    (set! (.-playbackRate el) 1)
    (set! (.-paused el) false)
    (session/attach! el opts)
    (reset! calls [])
    (.dispatchEvent el (js/Event. "play"))
    (is (= [[:playback-state "playing"]
            [:position {"duration" 60 "playbackRate" 1 "position" 10}]]
           @calls))))
```

To test the position in Safari's place, make `setPositionState` throw a
TypeError for an empty map or an infinite duration, as WebKit does.
`position!` then clears the state instead. play-nice's own browser tests
do this, in `session_browser_test.cljs`.
