play-nice
=========

[![Clojars Project](https://img.shields.io/clojars/v/dk.simongray/play-nice.svg)](https://clojars.org/dk.simongray/play-nice)

This is a ClojureScript library for the browser's
[Media Session](https://www.w3.org/TR/mediasession/), which shows what a
web page plays on the lock screen and lets the headphone buttons control
it.

- **Plain data.** The metadata, the details of a button press and the
  position on the lock screen are maps, and the handlers are a map of
  action to function.
- **The browsers' differences handled once.** Each engine refuses
  actions and position states of its own, and throws where the others
  don't. The functions catch that and return what was refused. Where the
  browser has no Media Session, they do nothing.
- **In step with a media element.** The session shows whether the
  element plays, and where it is when the browser can't tell by itself.
  Detaching hands both back to the browser.
- **The media element as data.** Its state is a map, and playing it
  returns the browser's answer, e.g. `{:refused :not-allowed}`, rather
  than a promise that rejects.

> This library was spun out of [podcast-clj](https://github.com/simongray/podcast-clj),
> a library for making podcast software, where it puts the episode that
> plays on the lock screen. Like podcast-clj, it was developed with
> assistance from frontier LLMs.

Getting started
---------------

For the latest release, add it from
[Clojars](https://clojars.org/dk.simongray/play-nice) to the `:deps` in
your `deps.edn`:

```clojure
dk.simongray/play-nice {:mvn/version "0.1.0"}
```

For changes that aren't released yet, use the SHA of the latest commit on
`master` instead:

```clojure
dk.simongray/play-nice
{:git/url "https://github.com/simongray/play-nice"
 :git/sha "…"}
```

Since shadow-cljs only reads Git dependencies from `deps.edn`, set
`:deps true` in your `shadow-cljs.edn` for that.

A player whose buttons just play, pause and seek the media element
`audio` needs three calls:

```clojure
(require '[dk.simongray.play-nice.session :as session])

(def detach!
  (session/attach! audio))

(session/metadata! {:title   "Blue in Green"
                    :artist  "Miles Davis"
                    :album   "Kind of Blue"
                    :artwork [{:src   "/covers/kind-of-blue-512.png"
                               :sizes "512x512"
                               :type  "image/png"}]})

(session/actions! (session/element-actions audio {:seekbackward 10
                                                  :seekforward  10}))
```

Call `metadata!` again for each new track, with nil when nothing plays,
and `(detach!)` when the player goes away. The
[quickstart](doc/quickstart.md) builds such a player from an empty
project. To follow the state of an app, e.g. in an atom or with
re-frame, see
[How to keep the lock screen in step with your app's state](doc/app-state.md).

The keys of the maps are the members of the spec's dictionaries in
kebab-case, e.g. `:chapter-info` for `chapterInfo`. The actions and the
spec's other enum values are keywords, e.g. `:seekto`. The
[reference](doc/reference.md) describes every map.

The buttons
-----------

Give `actions!` a map from each action to a function, which takes the
details of a press, e.g. `{:action :seekto :seek-time 754.2}`. Each call
sets exactly these handlers, clearing any that an earlier call set and
this one leaves out. It returns the set of actions that the browser
refused:

```clojure
(session/actions! {:play          play!
                   :pause         pause!
                   :seekto        #(seek! (:seek-time %))
                   :voiceactivity unmute!})
;; => #{:voiceactivity}   ; in Chrome and Firefox, which lack it
```

To offer only the buttons that work, call `session/support` first. It
also says whether the browser accepts a position state and chapters. To
test your handlers in Node, see
[How to test a player without the browser's session](doc/testing.md).

Merge your own handlers over those of `element-actions`, which play,
pause, stop and seek the element itself. Keep seekto among them: Safari
drops its own buttons once a page sets a handler, so without seekto its
lock screen has no scrubber. The skips need their seconds, since the
spec has no default.

A skip uses the seconds that the browser asks for, e.g. the 15 of
Safari's lock screen, and yours where it asks for none. To skip by yours
everywhere, and to make the next and previous buttons skip too, e.g. on
headphones, pass these options:

```clojure
(session/element-actions audio
                         {:seekbackward 15 :seekforward 30}
                         {:ignore-seek-offset true
                          :track-buttons      :skips})
```

For a previous button of your own, `previous-track` returns what it
should do, as Android's players have it, e.g. `:restart` 42 seconds into
a track and `:previous` within the first 3 seconds.

The position
------------

Every engine follows a lone media element by itself, so `attach!` leaves
the position to the browser, for reasons that
[the browsers' differences](doc/browsers.md) explains. Give it a
`:position-fn` where the browser can't follow, e.g. two players at once
in a crossfade, or a clip of a longer file:

```clojure
(session/attach! audio {:position-fn session/element-position})
```

The function takes the element's `:duration`, `:current-time`,
`:playback-rate`, `:paused` and `:error`, and returns the position
state, e.g. `{:duration 3600 :playback-rate 1.5 :position 754}`. For a
player without a media element, e.g. on Web Audio, call `position!` and
`playback-state!` yourself, as
[How to put a Web Audio player on the lock screen](doc/web-audio.md)
shows.

The media element
-----------------

The `media` namespace plays the element and reads its state as a map.
Playing returns a promise of the browser's answer, which never rejects:

```clojure
(require '[dk.simongray.play-nice.media :as media])

(-> (media/play! audio)
    (.then prn))
;; prints {:playing true}, or e.g. {:refused :not-allowed} before the
;; user has clicked
```

Where the browser refuses, `{:muted-fallback true}` plays the element
muted instead, and `autoplay/on-activation!` calls a function at the
user's next click, tap or key press.

To start an episode where the listener left off, give `source!` the
position and the rate along with the file. It sets them in the order
that the browser needs, since a new source resets the rate:

```clojure
(media/source! audio {:src "/272.mp3" :position 754 :playback-rate 1.5})
(media/play! audio)
```

To keep the element's state in your app's, give `watch!` a function. It
calls the function with the state now and whenever it changes, and
returns a function that stops it:

```clojure
(def stop!
  (media/watch! audio #(swap! app assoc :player %)))
;; :player is e.g. {:status :buffering :paused false :current-time 754.2
;;                  :duration 3600 :playback-rate 1.5 …}
```

The [reference](doc/reference.md) describes the state and every answer.

Chapters and captions
---------------------

Only Chromium accepts the chapters of the metadata, but every browser
follows a chapters track, which none of them shows. The `tracks`
namespace adds one from the chapters, and `watch!` says which chapter
plays, as the active cue of the track:

```clojure
(require '[dk.simongray.play-nice.tracks :as tracks])

(def remove-chapters!
  (tracks/add! audio {:kind :chapters
                      :cues (tracks/chapter-cues chapters duration)}))

(def stop!
  (tracks/watch! audio #(swap! app assoc :tracks %)))
;; :tracks is e.g. [{:kind :chapters :mode :hidden :active-cues
;;                   [{:start-time 95 :end-time 1830 :text "News"}] …}]
```

For a video, `show!` shows the captions or subtitles that best fit the
languages you prefer, and turns off the rest:

```clojure
(tracks/show! video {:kinds [:captions :subtitles] :languages ["da" "en"]})
```

Keyboard and seek bar
---------------------

In a player of your own, the `controls` namespace gives the keyboard the
handlers of the lock screen. Its keys work only while the focus is in
the player, as WCAG asks of shortcuts of a single key: the space bar and
k play and pause, j and l and the arrows skip, and the media keys do what
they say.

```clojure
(require '[dk.simongray.play-nice.controls :as controls])

(def handlers
  (session/element-actions audio {:seekbackward 10 :seekforward 30}))

(session/actions! handlers)

(def stop!
  (controls/listen! player audio handlers))
```

A seek bar of your own takes its ARIA attributes from `seek-aria`, with
the position in words for a screen reader, and its keys from
`seek-bar-keys`:

```clojure
[:div (merge {:aria-label "Position" :tabindex 0}
             (controls/seek-aria (media/state audio)))]
;; with {:role "slider" :aria-valuenow 750
;;       :aria-valuetext "12 minutes, 30 seconds" …}

(controls/listen! seek-bar audio handlers controls/seek-bar-keys)
```

What the browser can play
-------------------------

To choose among the files of an episode or a track, describe each as a
[Media Capabilities](https://www.w3.org/TR/media-capabilities/)
configuration, ask `capabilities/decoding-infos!` about them, and keep
those that `playable?` accepts:

```clojure
(require '[dk.simongray.play-nice.capabilities :as capabilities])

(def files
  [{:type :file :audio {:content-type "audio/ogg; codecs=opus"}}
   {:type :file :audio {:content-type "audio/mp4"}}])

(-> (capabilities/decoding-infos! files)
    (.then #(first (filter (comp capabilities/playable? %) files))))
;; => a promise of the Opus file, or of the AAC file where Opus won't play
```

On its own, Media Capabilities refuses some of the commonest files, e.g.
MP3 in Firefox and WebKit, as [the browsers' differences](doc/browsers.md)
explains. So where it says no or rejects a configuration, the answer also
has canPlayType's, e.g. `{:supported false :smooth false :power-efficient
false :can-play-type :maybe}`, and `playable?` accepts either. The
promise never rejects.

Artwork
-------

Each engine picks an image of the artwork its own way. Chrome on Android
skips those below 114 pixels and Safari those below 128, and both prefer
512. So offer a square PNG of 512 by 512, with its `:sizes` and `:type`.
The library leaves out an image whose URL doesn't parse, rather than
fail the call.

Only Chromium accepts chapters, and so far it shows them only in the media
controls of ChromeOS, behind a feature that's off by default. The library
passes them to every browser all the same, since the others ignore them.

Browsers
--------

Chrome, Edge, Firefox and Safari have the Media Session, but the Android
WebView doesn't, so an app in Capacitor or Cordova on Android needs a
native plugin for its lock screen. The [reference](doc/reference.md)
lists which browser supports which part, and what each engine refuses,
as of October 2026.

Development
-----------

```bash
npm install                            # once, for the tests
npx playwright install firefox webkit  # once, for the browsers
clojure -M:cljs compile test           # the tests in Node
clojure -M:cljs release browser-test   # the tests for a browser
node test/browsers.mjs                 # in Chrome, Firefox and WebKit
```

The browser tests play a real media element, and record each call on a
stand-in session, which can throw as Safari does. The last command runs
them in your Chrome and in Playwright's Firefox and WebKit, as CI does.
They compile with `release`, for advanced optimizations, which is where
a missing `^js` hint would show.
