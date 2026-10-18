# Quickstart: a player on the lock screen

In this tutorial, we build a web page that plays a track and shows it in
the browser's media controls, with its title, artist and cover. The
controls get buttons that skip ten seconds back and forward. You need
the [Clojure CLI](https://clojure.org/guides/install_clojure), Chrome,
an MP3 and a square PNG.

## The project

Make a folder for the project, and put this in its `deps.edn`:

```clojure
{:paths ["src"]
 :deps  {org.clojure/clojure    {:mvn/version "1.12.5"}
         thheller/shadow-cljs   {:mvn/version "3.5.3"}
         dk.simongray/play-nice {:mvn/version "0.3.0"}}}
```

Then put this in its `shadow-cljs.edn`. It defines a build called `app`
and a web server for the folder `public`. Its `:deps true` makes
shadow-cljs take the dependencies from `deps.edn`:

```clojure
{:deps     true
 :dev-http {8080 "public"}
 :builds   {:app {:target     :browser
                  :output-dir "public/js"
                  :modules    {:main {:init-fn player.core/init}}}}}
```

The page is `public/index.html`. Its `<audio>` element has the
`controls` attribute, so the browser shows its own play button and seek
bar:

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

The script calls `init` as soon as it has loaded, since the `:init-fn`
in `shadow-cljs.edn` names it. First, `init` logs what the browser
supports, from `support!`. Then `attach!` keeps the media controls in
step with the element, e.g. they show the track as paused when it
pauses. With `:actions`, it also sets their buttons, which play, pause
and skip the element. Last, `metadata!` shows the title, the artist and
the cover.

## Run it

Start the build and the server:

```bash
clojure -M -m shadow.cljs.devtools.cli watch app
```

The first build takes a minute or two. When it says
`[:app] Build completed.`, open <http://localhost:8080> in Chrome, and
open its developer console. The console shows what the browser
supports, e.g.

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
