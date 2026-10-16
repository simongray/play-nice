# How to test a player without the browser's session

The browser's MediaSession doesn't let a test read back the handlers
that a page set, and Node has no MediaSession at all. So give every call
of `session` the option `:session`, with a stand-in for the browser's
MediaSession that records what the calls set. Your tests can then check
what your player puts on the lock screen, and they run in Node too. Only
the tests of `metadata!` need a browser, since it builds the browser's
MediaMetadata.

## A stand-in session

The stand-in is a JavaScript object with the two methods that play-nice
calls, and a `playbackState` property. This one adds each call to an
atom, and keeps the handlers so that a test can press a button:

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
`"togglemicrophone"`. Then `actions!` returns that action.

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
    (set! (.-paused el) true)
    (session/attach! el opts)
    (reset! calls [])
    ;; the element starts to play, as a browser's would
    (set! (.-paused el) false)
    (.dispatchEvent el (js/Event. "play"))
    (is (= [[:playback-state "playing"]
            [:position {"duration" 60 "playbackRate" 1 "position" 10}]]
           @calls))))
```

To test how your player copes with Safari, make `setPositionState` throw
a TypeError for an empty map or an infinite duration, as WebKit does.
Then `position!` clears the state instead.

The functions of `media` take a stand-in for the element too, with the
attributes and methods they use, e.g. a `play` that returns a promise
rejected with a NotAllowedError, as a browser's does before the user has
clicked.

## Test what waits for a click

Give `autoplay/on-activation!` the option `:window` with an
`EventTarget` whose `navigator` has a `userActivation` of your own. Set
its `isActive` to true, and dispatch an event such as `pointerup`:

```clojure
(deftest after-a-click
  (let [played     (atom false)
        activation #js {:isActive false}
        ^js window (js/EventTarget.)]
    (set! (.-navigator window) #js {:userActivation activation})
    (autoplay/on-activation! #(reset! played true) {:window window})
    (set! (.-isActive activation) true)
    (.dispatchEvent window (js/Event. "pointerup"))
    (is (true? @played))))
```
