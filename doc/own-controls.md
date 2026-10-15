# Build a player with your own controls

In this tutorial we build a player with a play button, a seek bar and
keyboard shortcuts of its own, which starts where the listener left off
and shows on the lock screen. We build on the project of the
[quickstart](quickstart.md), so make that first.

## The page

Replace `public/index.html` with a page whose audio element has no
controls of its own, and our controls next to it:

```html
<!doctype html>
<html>
  <head>
    <meta charset="utf-8">
    <title>My player</title>
    <style>
      #seek-bar { height: 12px; background: #ddd; cursor: pointer;
                  touch-action: none; }
      #progress { height: 100%; width: 0; background: #333; }
    </style>
  </head>
  <body>
    <div id="player" tabindex="0">
      <audio></audio>
      <button id="play">Play</button>
      <div id="seek-bar" aria-label="Position" tabindex="0">
        <div id="progress"></div>
      </div>
    </div>
    <script src="js/main.js"></script>
  </body>
</html>
```

## The code

Replace `src/player/core.cljs` with this:

```clojure
(ns player.core
  (:require [dk.simongray.play-nice.controls :as controls]
            [dk.simongray.play-nice.media :as media]
            [dk.simongray.play-nice.session :as session]))

(def skips
  "The seconds of the skip buttons."
  {:seekbackward 15 :seekforward 30})

(defn by-id
  "The element of the page whose id is `id`."
  [id]
  (js/document.getElementById id))

(defn render!
  "Show the `state` of the audio element in the controls."
  [{:keys [paused current-time duration] :as state}]
  (set! (.-textContent (by-id "play")) (if paused "Play" "Pause"))
  (set! (.. (by-id "progress") -style -width)
        (str (if duration (* 100 (/ current-time duration)) 0) "%"))
  (doseq [[k v] (controls/seek-aria state)]
    (.setAttribute (by-id "seek-bar") (name k) v)))

(defn remember!
  "Show the `state` of the audio element, and keep its position for the
  next visit."
  [{:keys [current-time] :as state}]
  (render! state)
  (js/localStorage.setItem "position" current-time))

(defn toggle!
  "Play the `audio` element where it's paused, and pause it otherwise."
  [^js audio]
  (if (.-paused audio)
    (media/play! audio {:muted-fallback true})
    (media/pause! audio)))

(defn drag!
  "Seek the `audio` element where the pointer event `e` presses or drags
  the seek bar, with the seekto handler of the lock screen in `handlers`,
  roughly while it drags."
  [^js audio handlers ^js e]
  (let [^js bar (.-currentTarget e)
        id      (.-pointerId e)]
    (when (= "pointerdown" (.-type e))
      ;; the bar gets the pointer's moves while it's pressed, even off it
      (.setPointerCapture bar id))
    (when-let [details (and (.hasPointerCapture bar id)
                            (controls/pointer-seek e (media/state audio)))]
      ((:seekto handlers)
       (assoc details :fast-seek (= "pointermove" (.-type e)))))))

(defn init
  "Play track.mp3 from where the listener left off, with our controls."
  []
  (let [audio    (js/document.querySelector "audio")
        handlers (session/element-actions audio skips)]
    (media/source! audio {:src      "track.mp3"
                          :position (js/parseFloat
                                     (or (js/localStorage.getItem "position") 0))})
    (media/watch! audio remember! {:events (conj media/events :timeupdate)})
    (session/attach! audio {:actions skips})
    (session/metadata! {:title   "My first track"
                        :artist  "Me"
                        :artwork [{:src "cover.png" :sizes "512x512" :type "image/png"}]})
    (controls/listen! (by-id "player") audio handlers)
    (controls/listen! (by-id "seek-bar") audio handlers
                      {:keymap controls/seek-bar-keys})
    (.addEventListener (by-id "play") "click" #(toggle! audio))
    (doseq [event ["pointerdown" "pointermove" "pointerup"]]
      (.addEventListener (by-id "seek-bar") event #(drag! audio handlers %)))))
```

`source!` loads the track at the position of the last visit, which the
element moves to once it has the track's metadata. `watch!` calls
`remember!` with the state of the element now and at each change, and
`:timeupdate` adds the changes of its position while it plays, so that
the seek bar moves. To start something once the track has played to its
end, listen for the element's `ended` event rather than watch `:ended`,
which a seek to the end makes true too.

The handlers of `element-actions` are those of the lock screen, which
`attach!` sets with `:actions`. The keyboard presses them too: `listen!`
follows the keys pressed in the player, e.g. `k` to play and pause and
`l` to skip forward, and those of the seek bar, e.g. the arrow keys to
move a second. So does the pointer: `pointer-seek` turns a press of the
seek bar into the details that the lock screen's scrubber sends, and
while the pointer drags, they ask for a fast seek, which lands near the
point but sooner.

`play!` answers `{:playing true :muted true}` where the browser only lets
the page play muted, e.g. before the listener has clicked. Here the
button calls it, so the click lets the page play with sound.

## Run it

Start the build and the server as in the quickstart:

```bash
clojure -M -m shadow.cljs.devtools.cli watch app
```

Open <http://localhost:8080> in Chrome, and press Play. The button turns
into Pause, and the bar fills as the track plays. Click or drag the bar
to jump, or press Tab to move to it and use the arrow keys, Page Up and Page Down,
Home and End. A screen reader reads its position in words, e.g. "1
minute, 5 seconds". Chrome's media controls show the track, with buttons
that skip 15 seconds back and 30 forward.

Reload the page. The bar shows the position where you left off, and Play
goes on from there.

## What next

- [How to keep the lock screen in step with your app's state](app-state.md)
- The [reference](reference.md) of the answers, the state and the keys
