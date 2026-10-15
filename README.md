play-nice
=========

[![Clojars Project](https://img.shields.io/clojars/v/dk.simongray/play-nice.svg)](https://clojars.org/dk.simongray/play-nice)

*Make your media play nice in ClojureScript.*

This is a ClojureScript library for audio and video in the browser. It
gives you one idiomatic Clojure API for the
[media element](https://developer.mozilla.org/en-US/docs/Web/API/HTMLMediaElement)
and for the browser features that fit a player into the rest of the
system: the lock screen and the headphone buttons, Picture-in-Picture,
fullscreen, casting, captions, the keyboard and the rules of autoplay.

- **Data in, data out.** The state of an element is a map, the browser's
  names are keywords, and what you do to an element can be data too,
  e.g. as effects in [re-frame](https://day8.github.io/re-frame/).
- **One shape everywhere.** A `watch!` calls your function with a map
  now and at each change, and returns a function that stops it. A
  promise answers with a map, e.g. `{:refused :not-allowed}`, and never
  rejects.
- **The browsers' differences handled once.** Chrome, Firefox and
  Safari each refuse, throw and leave out things of their own, and the
  iPhone more so. The tests run in each of them, the iPhone included.
- **Pure where it can be.** The parts that need no browser run on the
  JVM too, and the rest takes stand-ins in a test.

Here a podcast episode starts where the listener left off, at their
rate, and shows on the lock screen with buttons that skip:

```clojure
(require '[dk.simongray.play-nice.media :as media]
         '[dk.simongray.play-nice.session :as session])

(media/source! audio {:src "/272.mp3" :position 754 :playback-rate 1.5})
(media/watch! audio #(swap! app assoc :player %))

(session/attach! audio {:actions {:seekbackward 15 :seekforward 30}})
(session/metadata! {:title "Episode 272" :artist "My Podcast"})

(-> (media/play! audio {:muted-fallback true})
    (.then prn))
;; prints {:playing true}, or {:playing true :muted true} where the
;; browser lets the page play only muted
```

The library works on its own, and [with podcast-clj](#with-podcast-clj).

> This library was spun out of [podcast-clj](https://github.com/simongray/podcast-clj),
> a library for making podcast software, where it puts the episode that
> plays on the lock screen. Like podcast-clj, it was developed with
> assistance from frontier LLMs.


Hard to find elsewhere
----------------------

Some of what the library does, you'd otherwise find only inside a large
player, or not at all:

- [Lock screen buttons that work in Safari](#the-lock-screen), which
  drops its own buttons once a page sets one.
- [Chapters in every browser](#chapters-and-captions), as a hidden text
  track, since only Chromium takes them as metadata.
- [Fullscreen on an iPhone](#fullscreen), in the system's video player
  where nothing else may go fullscreen.
- [The iPhone's locked volume](#the-media-element), which a page can't
  read, found out from the `volumechange` event that never comes.
- [One player across the tabs of an app](#around-the-player), so that
  two tabs don't play at once.
- [MP3 and AAC found playable](#choosing-a-file) where Media
  Capabilities says they aren't.
- [Keyboard shortcuts that WCAG allows](#your-own-controls), and a seek
  bar that a screen reader reads in the user's language.
- [Caption styles](#chapters-and-captions) in the vocabulary of the
  FCC's rule for caption displays.


Getting started
---------------

Add the latest release from
[Clojars](https://clojars.org/dk.simongray/play-nice) to the `:deps` of
your `deps.edn`:

```clojure
dk.simongray/play-nice {:mvn/version "0.1.0"}
```

This README describes the code on `master`, which has changed a lot
since 0.1.0, and the docs of 0.1.0 are on
[cljdoc](https://cljdoc.org/d/dk.simongray/play-nice). To use `master`,
give the SHA of its latest commit instead, and set `:deps true` in your
`shadow-cljs.edn`, since [shadow-cljs](https://github.com/thheller/shadow-cljs)
only reads Git dependencies from `deps.edn`:

```clojure
dk.simongray/play-nice
{:git/url "https://github.com/simongray/play-nice"
 :git/sha "…"}
```

The [quickstart](doc/quickstart.md) puts a player on the lock screen in a
new project, and [Build a player with your own controls](doc/own-controls.md)
gives it a play button, a seek bar and keyboard shortcuts.


How the API works
-----------------

A function takes the element first, and a map of options last, which
you can leave out. A name that ends in `!` changes the page or returns a
promise, and the others only read or compute.

The keys of a map are the spec's names in kebab-case, e.g.
`:current-time` for `currentTime`, and the values of the spec's enums are
keywords, e.g. `:seekto`. Where the browser refuses something, the answer
says why with a keyword, e.g. `{:refused :not-allowed}`. Where it lacks a
feature, the functions do nothing.

The namespaces are under `dk.simongray.play-nice`, in families: `media`
for the element itself, `display` for where it shows, and `page` for the
page around it, beside `session`, `controls` and `schema`:

| Namespace | For |
|---|---|
| `media` | Playing, seeking, the rate and the volume, and the element's state |
| `media.tracks` | Chapters, and the captions or subtitles to show |
| `media.captions` | The user's style of captions |
| `media.capabilities` | Which file of an episode the browser can play |
| `media.fragment` | The times in a file's URL and in a page's link |
| `session` | The lock screen and the headphone buttons |
| `controls` | Keyboard shortcuts and a seek bar of your own |
| `display.picture-in-picture` | Picture-in-Picture of a video |
| `display.fullscreen` | Fullscreen, also on an iPhone |
| `display.remote-playback` | Casting and AirPlay |
| `display.wake-lock` | The screen kept on while the media plays |
| `page.autoplay` | The browser's autoplay policy, the user's next click, and a muted video that plays while it's in view |
| `page.tabs` | One player at a time across the tabs of an app |
| `page.embed` | The `allow` attribute of an iframe that embeds a player |
| `schema` | Malli schemas of the maps |

Of these, `media.fragment`, `media.captions`, `controls`, `page.embed`
and `schema` also run on the JVM, e.g. to write a page on the server. Of
their functions, only `captions/style!` and `controls/listen!` need a
browser.


The media element
-----------------

The `media` namespace plays the element, seeks, and sets its rate and
volume. To start an episode where the listener left off, give `source!`
the position and the rate along with the file, which it sets in the
order that the browser needs:

```clojure
(media/source! audio {:src "/272.mp3" :position 754 :playback-rate 1.5})
```

Playing returns a promise of the browser's answer, e.g. `{:playing
true}`, or `{:refused :not-allowed}` before the user has clicked. A
browser usually lets a page play sound only after a click, a tap or a
key press, as
[MDN's guide to autoplay](https://developer.mozilla.org/en-US/docs/Web/Media/Guides/Autoplay)
explains. So call `play!` in the handler of the click that asks for it,
since it calls the browser before it returns, while the click still
counts. Where the browser refuses the sound, `{:muted-fallback true}`
plays the element muted instead.

To keep the element's state in your app's, give `watch!` a function. It
calls the function with the state now and at each change, e.g. a pause,
a seek or a new rate:

```clojure
(media/watch! audio #(swap! app assoc :player %)
              {:events (conj media/events :timeupdate)})
;; :player is e.g. {:status :buffering :paused false :current-time 754.2
;;                  :duration 3600 :playback-rate 1.5 …}
```

The `:timeupdate` event, which fires several times a second while the
element plays, is left out by default, so add it for a seek bar that
moves as it plays.

Where your effects are data, as in re-frame, `apply!` does what each
effect names, in order, and returns a promise of the answers:

```clojure
(rf/reg-fx :media #(media/apply! audio %))

(rf/reg-event-fx :player/resume
  (fn [_ [_ position]]
    {:fx [[:media [[:seek position] [:play]]]]}))
```

In re-frame, dispatch the event of a click with `dispatch-sync`, so that
its effects run while the click still counts.
[How to keep the lock screen in step with your app's state](doc/app-state.md#with-re-frame)
brings the answers back as an event too.

A volume slider sounds even along its length on the `:cubic` curve, e.g.
`(media/volume! audio 0.5 {:curve :cubic})`. An iPhone plays every
element at the system's volume, whatever the page sets, so hide the
slider where `volume-locked!` resolves to true. The
[reference](doc/reference.md#the-media-element) has the state, the
effects and every answer.


The lock screen
---------------

The `session` namespace puts what plays on the lock screen and in the
browser's media controls, and lets the headphone buttons control it,
through the
[Media Session](https://developer.mozilla.org/en-US/docs/Web/API/Media_Session_API).
Two calls are enough for a player whose buttons play, pause and skip the
element, as in the example at the top: `attach!` keeps the session in
step with the element and sets its buttons, and `metadata!` shows the
title, artist and artwork. Call `metadata!` again for each new episode,
with nil when nothing plays, and call the function that `attach!`
returns when the player goes away.

For buttons of your own, e.g. the next episode in a queue, merge your
handlers over the element's own and set them with `actions!`, which
returns the set of buttons that the browser refused:

```clojure
(session/actions! (assoc (session/element-actions audio {:seekbackward 15
                                                         :seekforward  30})
                         :nexttrack (fn [_] (play-next!))))
;; => #{}
```

Keep `:seekto` among the handlers, as `element-actions` does, since
[Safari drops its own buttons](doc/browsers.md#safaris-own-buttons) once
a page sets one, and its lock screen has no scrubber without seekto. For
a player without a media element, e.g. on
[Web Audio](https://developer.mozilla.org/en-US/docs/Web/API/Web_Audio_API),
see [How to put a Web Audio player on the lock screen](doc/web-audio.md),
and to test your handlers in Node,
[How to test a player without the browser's session](doc/testing.md).


Your own controls
-----------------

For a player with controls of your own, the `controls` namespace gives
the keyboard the handlers of the lock screen. The keys work only while
the focus is in the player, as
[WCAG asks of shortcuts of one key](https://www.w3.org/WAI/WCAG22/Understanding/character-key-shortcuts):
the space bar and `k` play and pause, `j`, `l` and the arrows skip, and
the media keys do what they say.

A seek bar of your own takes its
[ARIA attributes](https://www.w3.org/WAI/ARIA/apg/patterns/slider/examples/slider-seek/)
from `seek-aria`, with the position in words for a screen reader, in the
user's language, and its keys from `seek-bar-keys`:

```clojure
(require '[dk.simongray.play-nice.controls :as controls])

(def handlers
  (session/element-actions audio {:seekbackward 15 :seekforward 30}))

(controls/listen! player audio handlers)
(controls/listen! seek-bar audio handlers {:keymap controls/seek-bar-keys})

(controls/seek-aria (media/state audio))
;; => {:role "slider" :aria-valuemin 0 :aria-valuemax 3600
;;     :aria-valuenow 754 :aria-valuetext "12 minutes, 34 seconds"}
```

[Build a player with your own controls](doc/own-controls.md) puts these
together into a player.


Chapters and captions
---------------------

Only Chromium takes the chapters of the Media Session's metadata, but
every browser follows a
[text track](https://developer.mozilla.org/en-US/docs/Web/API/WebVTT_API)
of chapters, without showing it. The `media.tracks` namespace adds one, and
its `watch!` says which chapter plays. Add the track once the metadata
has loaded, since the last chapter lasts until the duration:

```clojure
(require '[dk.simongray.play-nice.media.tracks :as tracks])

(tracks/add! audio {:kind :chapters
                    :cues (tracks/chapter-cues chapters (.-duration audio))})

(tracks/watch! audio #(swap! app assoc :tracks %))
;; :tracks is e.g. [{:kind :chapters :mode :hidden :active-cues
;;                   [{:start-time 95 :end-time 1830 :text "News"}] …}]
```

For a video, `tracks/show!` shows the captions or subtitles that best
fit the languages you prefer. A page can't read the system's own caption
settings, so keep the user's style, and `captions/style!` turns it into
[CSS for the captions](https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Selectors/::cue)
of that video. The style has the settings that
[the FCC's rule for caption displays](https://www.ecfr.gov/current/title-47/chapter-I/subchapter-C/part-79/subpart-B/section-79.103)
names, e.g. the size, the colours and the edges of the letters:

```clojure
(require '[dk.simongray.play-nice.media.captions :as captions])

(tracks/show! video {:kinds [:captions :subtitles] :languages ["da" "en"]})
(captions/style! video {:font-scale 1.5 :color :yellow :edge :drop-shadow})
```


Picture-in-Picture, fullscreen and casting
------------------------------------------

The browser refuses each of these without a click, so call them in a
click handler. Each answers with data, and has a `watch!` that follows
its state.

### Picture-in-Picture

For a video, `picture-in-picture/enter!` shows it in a floating window,
as [Picture-in-Picture](https://developer.mozilla.org/en-US/docs/Web/API/Picture-in-Picture_API)
has it, and answers with the size of the window, or why it couldn't:

```clojure
(require '[dk.simongray.play-nice.display.picture-in-picture :as pip])

(-> (pip/enter! video)
    (.then prn))
;; prints {:picture-in-picture true :width 320 :height 180}, or e.g.
;; {:refused :disabled} for a video with disablePictureInPicture
```

### Fullscreen

On an iPhone, only a video element itself can go
[fullscreen](https://developer.mozilla.org/en-US/docs/Web/API/Fullscreen_API),
in the system's own player. So `fullscreen/enter!` puts your player
fullscreen where it can, and falls back to the video where it can't:

```clojure
(require '[dk.simongray.play-nice.display.fullscreen :as fullscreen])

(-> (fullscreen/enter! player {:video video :orientation :landscape})
    (.then prn))
;; prints {:fullscreen :element …} on a desktop, {:fullscreen :video …} on
;; an iPhone, or e.g. {:refused :not-allowed}
```

### Casting

To cast, `remote-playback/prompt!` shows the browser's picker of devices,
e.g. a [Chromecast](https://developers.google.com/cast) or an
[AirPlay](https://www.apple.com/airplay/) receiver, through
[Remote Playback](https://developer.mozilla.org/en-US/docs/Web/API/Remote_Playback_API).
Its `watch!` says whether a device is available, which decides whether
to show a cast button. Firefox has no casting, so there `prompt!` answers
`{:refused :not-supported}`.


Autoplay
--------

Before the user's first click, a browser usually plays a page's media
only muted, if at all. To start what it refused to play,
`autoplay/on-activation!` calls a function at the user's next click, tap
or key press. Where the browser tells its policy, which only Firefox
does so far, `autoplay/policy` returns it:

```clojure
(require '[dk.simongray.play-nice.page.autoplay :as autoplay])

(autoplay/on-activation! #(media/play! audio))
```

For a muted video at the top of a page, `autoplay/play-in-view!` plays it
while it's in view and pauses it when it scrolls away, as Chrome and
Safari on an iPhone do for a video with the `autoplay` attribute, but
in every browser. It leaves the video alone once
the visitor pauses it, and holds it where the user
[prefers reduced motion](https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/At-rules/@media/prefers-reduced-motion).


Around the player
-----------------

On a desktop, two tabs of the same app play at once, since no browser
stops one tab's audio when another starts. So `tabs/attach!` holds a
[Web Lock](https://developer.mozilla.org/en-US/docs/Web/API/Web_Locks_API)
named for your app while the element plays, and pauses the element when
another tab of the app takes the lock.

A playing video keeps the screen on, but no browser does that for audio,
so a transcript goes dark while an episode plays. To keep the screen on
while the element plays, `wake-lock/hold-while-playing!` holds a
[Screen Wake Lock](https://developer.mozilla.org/en-US/docs/Web/API/Screen_Wake_Lock_API):

```clojure
(require '[dk.simongray.play-nice.page.tabs :as tabs]
         '[dk.simongray.play-nice.display.wake-lock :as wake-lock])

(tabs/attach! audio "my-podcast-app")
(wake-lock/hold-while-playing! audio)
```


Choosing a file
---------------

An episode often comes in several files, e.g. Opus and AAC. To choose
the one to play, describe each as a configuration of
[Media Capabilities](https://developer.mozilla.org/en-US/docs/Web/API/Media_Capabilities_API),
ask `capabilities/decoding-infos!` about them, and keep those that
`playable?` accepts:

```clojure
(require '[dk.simongray.play-nice.media.capabilities :as capabilities])

(def files
  [{:type :file :audio {:content-type "audio/ogg; codecs=opus"}}
   {:type :file :audio {:content-type "audio/mp4"}}])

(-> (capabilities/decoding-infos! files)
    (.then #(first (filter (comp capabilities/playable? %) files))))
;; => a promise of the Opus file, or of the AAC file where Opus won't play
```

On its own, Media Capabilities refuses some of the commonest files, e.g.
MP3 in Firefox and WebKit, so the answer also has what
[canPlayType](https://developer.mozilla.org/en-US/docs/Web/API/HTMLMediaElement/canPlayType)
says, as [the browsers' differences](doc/browsers.md#two-answers-about-what-plays)
explains.


Links, embeds and schemas
-------------------------

A link can point at a moment of an episode. For a file's URL,
`fragment/time` reads the time of its
[media fragment](https://www.w3.org/TR/media-frags/), e.g. `#t=73,133`,
and for a page's link, `fragment/link-time` reads its `t`, e.g.
`?t=1m13s`. With `session/clip-position`, the lock screen then shows
such a soundbite rather than the whole episode.

A page that embeds a player from another site in an
[iframe](https://developer.mozilla.org/en-US/docs/Web/HTML/Reference/Elements/iframe)
has to [allow](https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/Permissions_Policy)
what it may do, e.g. go fullscreen, and `embed/allow` writes the
attribute:

```clojure
(require '[dk.simongray.play-nice.page.embed :as embed])

(embed/allow {:autoplay true :fullscreen true :picture-in-picture true})
;; => "autoplay; fullscreen; picture-in-picture"
```

The `schema` namespace has the shapes of the maps as
[Malli](https://github.com/metosin/malli) schemas, written as plain data,
so the library doesn't depend on Malli. Check a map with Malli of your
own, e.g. in a test: `(m/validate schema/media-effect [:seek 754])`.


With podcast-clj
----------------

A podcast app in the browser needs both libraries:
[podcast-clj](https://github.com/simongray/podcast-clj) for the feeds and
the listener's queue and progress, and play-nice for the playing.
Neither depends on the other, but they share their data. What
`library/playback` returns for an episode goes straight to play-nice:

```clojure
(require '[dk.simongray.podcast.app.library :as library])

(let [{:keys [src position rate metadata]} (library/playback @lib id)]
  (media/source! audio {:src src :position position :playback-rate rate})
  (session/metadata! metadata))
```

Its `resolve/decoding-configuration` describes a file of a feed for
`capabilities/decoding-info!`, and its `links/at-time` writes the links
to a moment that `fragment/time` reads. Its guide to
[playing episodes](https://github.com/simongray/podcast-clj/blob/master/doc/building-an-app.md#play-episodes)
puts the two together.


Documentation
-------------

- **Tutorials:** the [quickstart](doc/quickstart.md), and
  [Build a player with your own controls](doc/own-controls.md).
- **How-to guides:**
  [keep the lock screen in step with your app's state](doc/app-state.md),
  [put a Web Audio player on the lock screen](doc/web-audio.md), and
  [test a player without the browser's session](doc/testing.md).
- **Reference:** [the maps, options and answers](doc/reference.md), and
  the docstrings on [cljdoc](https://cljdoc.org/d/dk.simongray/play-nice).
- **Explanation:** [the browsers' differences](doc/browsers.md), and why
  the library deals with them as it does.


Browsers
--------

The library is for Chrome, Edge, Firefox and Safari. Its tests run in
Chrome, Firefox and WebKit, and in Safari on an iPhone simulator, and
Edge has Chrome's engine. The Android WebView has no Media Session, so
an app in Capacitor or Cordova on Android needs a native plugin for its
lock screen. The [reference](doc/reference.md#browser-support) lists
which browser supports which part, and what each engine refuses, as of
October 2026.


Development
-----------

```bash
npm install                            # once, for the tests
npx playwright install firefox webkit  # once, for the browsers
clojure -M:cljs compile test           # the tests in Node
clojure -X:test                        # the .cljc tests on the JVM
clojure -M:cljs release browser-test   # the tests for a browser
node test/browsers.mjs                 # in Chrome, Firefox and WebKit
node test/simulator.mjs                # in Safari on an iOS simulator
```

The browser tests play a real media element, and record each call on a
stand-in session, which can throw as Safari does. The last command runs
them in your Chrome and in Playwright's Firefox and WebKit, as CI does.
They compile with `release`, for advanced optimizations, which is where
a missing `^js` hint would show. With Xcode, the same build also runs in
Safari on an iPhone simulator, which CI doesn't do.
