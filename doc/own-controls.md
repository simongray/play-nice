# Build a player with your own controls

In this tutorial, we replace the browser's controls with a play button,
a seek bar and keyboard shortcuts of our own. The player starts where
the listener left off, and shows on the lock screen. We build on the
project of the [quickstart](quickstart.md), so make that first.

The seek bar here is an element of our own, which shows everything that
the `controls` namespace does for one. A range input,
`<input type="range">`, needs less, as the README's
[Your own controls](../README.md#your-own-controls) shows.

## The page

Replace `public/index.html` with this page. Its audio element has no
`controls` attribute, so the browser shows no controls of its own, and
ours sit next to it:

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

The keys work only where the focus is, so the `tabindex` lets the player
and the seek bar take the focus. With `touch-action: none`, a drag on
the seek bar doesn't scroll the page on a touch screen.

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
  (doseq [[k v] (controls/slider-attributes state)]
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
    (media/play! audio)
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
                            (controls/slider-seek e (media/state audio)))]
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
                      {:keymap controls/slider-keys})
    (.addEventListener (by-id "play") "click" #(toggle! audio))
    (doseq [event ["pointerdown" "pointermove" "pointerup"]]
      (.addEventListener (by-id "seek-bar") event #(drag! audio handlers %)))))
```

In `init`, `source!` loads the track at the position that `remember!`
kept in the browser's `localStorage` at the last visit. The element
moves there once it has the track's metadata. Then `watch!` calls
`remember!` with the element's state now and at each change. With
`:timeupdate`, the changes include the position while the element plays,
so that the seek bar moves.

The handlers of `element-actions` are the same as those that `attach!`
gives the lock screen with `:actions`, and the keyboard calls them too.
The first `listen!` follows the keys pressed in the player, e.g. k to
play and pause, and l to skip forward. The second follows the keys of
the seek bar, e.g. the arrow keys to move a second. The pointer calls
the same `:seekto` handler as the lock screen's scrubber: in `drag!`,
`slider-seek` turns a press on the seek bar into the details of a seek.
While the pointer drags, the details ask for a fast seek, which lands
near the point but sooner.

## Run it

Start the build and the server as in the quickstart:

```bash
clojure -M -m shadow.cljs.devtools.cli watch app
```

Open <http://localhost:8080> in Chrome, and press Play. The button turns
into Pause, and the bar fills as the track plays. Click or drag the bar
to jump. You can also press Tab to move the focus to the bar, and then
use the arrow keys, Page Up and Page Down, or Home and End. A screen
reader reads the bar's position in words, e.g. "1 minute, 5 seconds".
Chrome's media controls show the track, with buttons that skip 15
seconds back and 30 forward.

Reload the page. The bar shows the position where you left off, and Play
goes on from there.

## What next

- [How to keep the lock screen in step with your app's state](app-state.md)
- The [reference](reference.md) of the answers, the state and the keys
