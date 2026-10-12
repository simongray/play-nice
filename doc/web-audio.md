# How to put a Web Audio player on the lock screen

A player that plays through Web Audio has no media element for `attach!`
to follow, so set the playback state and the position yourself, besides
the metadata and the handlers. Each engine also needs something of its
own before it shows such a player at all.

## Set the state at each change

Write three functions for the moments that change what the lock screen
shows:

```clojure
(require '[dk.simongray.play-nice.session :as session])

(defn playing!
  "Show that the player plays a track of `duration` seconds from
  `position` at `rate`."
  [duration position rate]
  (session/playback-state! :playing)
  (session/position! {:duration      duration
                      :position      position
                      :playback-rate rate}))

(defn paused!
  "Show that the player paused `position` seconds into a track of
  `duration` seconds."
  [duration position]
  (session/playback-state! :paused)
  (session/position! {:duration duration :position position}))

(defn stopped!
  "Show that the player stopped."
  []
  (session/playback-state! :none)
  (session/position! nil))
```

Call `playing!` when the player starts, and again after each seek and
change of rate, `paused!` when it pauses, and `stopped!` when it stops.
Set the metadata with `session/metadata!` as for any player. The browser
can't play or seek the player itself, so give `session/actions!` handlers
for at least `:play`, `:pause` and `:seekto` that do it.

## In Safari

Safari shows a Web Audio player only when the page says that it plays
audio, through the [Audio Session](https://www.w3.org/TR/audio-session/)
API, which play-nice doesn't wrap yet. Set its type before the
AudioContext starts:

```clojure
(let [^js navigator js/navigator]
  (when-let [^js audio-session (.-audioSession navigator)]
    (set! (.-type audio-session) "playback")))
```

Safari then ignores the playback state and the handlers. Its play and
pause buttons resume and suspend the AudioContext themselves.

## In Chrome and Edge

Chromium shows the controls while the AudioContext makes sound and the
playback state isn't `:none`, so `playing!` is what makes them appear.
It drops them after about two seconds of silence, or as soon as you
suspend the AudioContext.

## In Firefox

Firefox doesn't show a Web Audio player yet. Mozilla plans to, in
[bug 2067534](https://bugzilla.mozilla.org/show_bug.cgi?id=2067534).

These differences are from
[w3c/mediasession#378](https://github.com/w3c/mediasession/issues/378),
which Mozilla opened in September 2026 to align the browsers.
