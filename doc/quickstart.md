# Quickstart: a player on the lock screen

In this tutorial we build a web page that plays a track and shows it in
the browser's media controls, with its title, artist and cover, and
buttons that skip ten seconds. You need the Clojure CLI, Chrome, an MP3
and a square PNG.

## The project

Make a folder for the project, and put this in its `deps.edn`:

```clojure
{:paths ["src"]
 :deps  {org.clojure/clojure    {:mvn/version "1.12.5"}
         thheller/shadow-cljs   {:mvn/version "3.5.3"}
         dk.simongray/play-nice {:mvn/version "0.2.0"}}}
```

Then this in its `shadow-cljs.edn`, for a build called `app` and a
server for the folder `public`:

```clojure
{:deps     true
 :dev-http {8080 "public"}
 :builds   {:app {:target     :browser
                  :output-dir "public/js"
                  :modules    {:main {:init-fn player.core/init}}}}}
```

The page is `public/index.html`, with an audio element:

```html
<!doctype html>
<html>
  <head>
    <meta charset="utf-8">
    <title>My player</title>
  </head>
  <body>
    <audio controls src="track.mp3"></audio>
    <script src="js/main.js"></script>
  </body>
</html>
```

Copy your MP3 into `public` as `track.mp3`, and your PNG as `cover.png`.

## The code

Put this in `src/player/core.cljs`:

```clojure
(ns player.core
  (:require [dk.simongray.play-nice.session :as session]))

(defn init
  "Put the page's audio element on the lock screen."
  []
  (let [audio (js/document.querySelector "audio")]
    (js/console.log (pr-str (session/support!)))
    (session/attach! audio {:actions {:seekbackward 10 :seekforward 10}})
    (session/metadata! {:title   "My first track"
                        :artist  "Me"
                        :artwork [{:src   "cover.png"
                                   :sizes "512x512"
                                   :type  "image/png"}]})))
```

`attach!` keeps the media controls in step with the element, and with
`:actions` it sets their buttons, which play, pause and skip it.
`metadata!` shows the title, artist and cover. `support!` comes first,
since it finds out what the browser supports by clearing each button.

## Run it

Start the build and the server:

```bash
clojure -M -m shadow.cljs.devtools.cli watch app
```

The first build takes a minute or two. When it says
`[:app] Build completed.`, open <http://localhost:8080> in Chrome and its
developer console. The console shows what the browser supports, e.g.

```clojure
{:actions #{:play :pause :seekto :seekbackward :seekforward …}
 :position-state? true
 :chapter-info? true}
```

## Use the media controls

Press play on the page. Chrome's toolbar now has a button for media
controls. Click it, and you'll see "My first track" by "Me" with your
cover, and buttons that skip ten seconds back and forward. Press the
forward one, and the audio element jumps ten seconds ahead. On a Mac,
Now Playing in the Control Center shows the same track and buttons.

## What next

- [Build a player with your own controls](own-controls.md)
- [How to keep the lock screen in step with your app's state](app-state.md)
- [Reference](reference.md)
- [The browsers' differences](browsers.md)
