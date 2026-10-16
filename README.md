play-nice
=========

[![Clojars Project](https://img.shields.io/clojars/v/dk.simongray/play-nice.svg)](https://clojars.org/dk.simongray/play-nice)

This is a ClojureScript library that makes audio and video _play nice_ in the browser. It
gives you one idiomatic Clojure API for the
[media element](https://developer.mozilla.org/en-US/docs/Web/API/HTMLMediaElement)
and for the other browser features that deal with media:
[the lock screen and the headphone buttons](https://developer.mozilla.org/en-US/docs/Web/API/Media_Session_API),
[Picture-in-Picture](https://developer.mozilla.org/en-US/docs/Web/API/Picture-in-Picture_API),
[fullscreen](https://developer.mozilla.org/en-US/docs/Web/API/Fullscreen_API),
[casting](https://developer.mozilla.org/en-US/docs/Web/API/Remote_Playback_API),
[captions](https://developer.mozilla.org/en-US/docs/Web/API/WebVTT_API),
[the keyboard](https://developer.mozilla.org/en-US/docs/Web/Accessibility/Guides/Keyboard-navigable_JavaScript_widgets)
and [the rules of autoplay](https://developer.mozilla.org/en-US/docs/Web/Media/Guides/Autoplay).

- **Browser differences.** Chrome, Firefox, and Safari (especially on iPhone) all handle media slightly differently,
  so play-nice unifies the API and irons out the differences in behaviour.
- **Data-driven.** It is an idiomatic Clojure layer on top of the stateful browser API.
  State is represented as maps, browser jargon as keywords, and changes are pure data too.
- **Pure where it can be.** While it is a ClojureScript library, the pure parts are written in CLJC and can run on the JVM too.

> This library was spun out of [podcast-clj](https://github.com/simongray/podcast-clj),
> a library for making podcast software, where it puts the episode that
> plays on the lock screen. Like podcast-clj, it was developed with
> assistance from frontier LLMs. It was _especially_ beneficial when I was probing for subtle browser differences.

Hard to find elsewhere
----------------------

A lot of what this library does you'd be hard pressed to find a unified API for elsewhere:

- [A single player across the tabs of an app](#around-the-player), so that two tabs don't play at once.
- [Chapters in every browser](#chapters-and-captions), as a hidden text track, since only Chromium currently accepts chapters as metadata.
- [MP3 and AAC found playable](#choosing-a-file) even when the browser API says they can't be.
- [Keyboard shortcuts that WCAG recommends](#your-own-controls), and a seek bar that a screen reader reads in the user's language.
- [Caption styles](#chapters-and-captions) in the vocabulary of the FCC's rule for caption displays.
- [Fullscreen on an iPhone](#picture-in-picture-fullscreen-and-casting), using the system's video player as nothing else may go fullscreen.
- [The iPhone's locked volume](#the-media-element), which is otherwise tricky to find out.
- [Lock screen buttons that work in Safari](#the-lock-screen), which drops its own buttons once a page sets one.


Getting started
---------------

Add the latest release from
[Clojars](https://clojars.org/dk.simongray/play-nice) to the `:deps` of
your `deps.edn`:

```clojure
dk.simongray/play-nice {:mvn/version "0.2.0"}
```

For changes since the latest release, give a Git dependency with the SHA
of the latest commit on `master` instead, and set `:deps true` in your
`shadow-cljs.edn`, since
[shadow-cljs](https://github.com/thheller/shadow-cljs) only reads Git
dependencies from `deps.edn`. The docstrings of each release are on
[cljdoc](https://cljdoc.org/d/dk.simongray/play-nice).

The [quickstart](doc/quickstart.md) puts a player on the lock screen in a
new project, and [Build a player with your own controls](doc/own-controls.md)
gives it a play button, a seek bar and keyboard shortcuts.


A podcast example
-----------------

A podcast episode should start where the listener left off, at their chosen playback rate,
and should show skip buttons on the phone's lock screen:

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

**NOTE:** the library works well on its own, but it was also designed to be paired with [with podcast-clj](#with-podcast-clj).


How the API works
-----------------

A function takes the element first, and a map of options last (which you can leave out).
A function name that ends in `!` changes the page or returns a promise.
Other functions are pure.

The keys of a map are the spec's names in kebab-case, e.g.
`:current-time` for `currentTime`, and the values of the spec's enums are
keywords, e.g. `:seekto`. Where the browser refuses something, the answer
says why with a keyword, e.g. `{:refused :not-allowed}`. Where it lacks a
feature, the functions do nothing.

The namespaces, under `dk.simongray.play-nice`, come in families:

- `media` for the element itself, and its tracks, captions, capabilities
  and fragments
- `display` for where it shows: Picture-in-Picture, fullscreen, casting
  and the wake lock
- `page` for the page around it: autoplay, tabs and embeds
- `session`, `controls` and `schema` on their own

The [reference](doc/reference.md#namespaces) lists each of them, and
those that also run on the JVM.


The media element
-----------------

The `media` namespace plays the element, seeks, and sets its rate and
volume. To start an episode, `source!` loads the file with the position
and the rate, in the order that the browser needs. A browser usually
lets a page play sound only after a click, a tap or a key press, as
[MDN's guide to autoplay](https://developer.mozilla.org/en-US/docs/Web/Media/Guides/Autoplay)
explains, so call `play!` in the handler of the click itself.

To keep the element's state in your app's, give `watch!` a function.
Follow `:timeupdate` too, which fires several times a second while the
element plays, for a seek bar that moves:

```clojure
(media/watch! audio #(swap! app assoc :player %)
              {:events (conj media/events :timeupdate)})
;; :player is e.g. {:status :buffering :paused false :current-time 754.2 …}
```

Where your effects are data, as in re-frame, `apply!` does what effects
such as `[:seek 754]` and `[:play]` name, as
[the app state guide](doc/app-state.md#with-re-frame) shows. A volume
slider sounds even along its length on the `:cubic` curve, and
`volume-locked!` tells where an iPhone ignores the volume a page sets.
The [reference](doc/reference.md#the-media-element) has the state, the
effects and every answer.


The lock screen
---------------

The `session` namespace puts what plays on the lock screen and in the
browser's media controls, and lets the headphone buttons control it,
through the
[Media Session](https://developer.mozilla.org/en-US/docs/Web/API/Media_Session_API).
In the example at the top, `attach!` keeps the session in step with the
element and sets buttons that play, pause and skip it, and `metadata!`
shows the title, artist and artwork.

For buttons of your own, e.g. the next episode, merge your handlers over
those of `element-actions` and give them to `actions!`, which returns the
buttons that the browser refused. Keep `:seekto` among them, since
[Safari drops its own buttons](doc/browsers.md#safaris-own-buttons) once
a page sets one. How-to guides show
[a player on Web Audio](doc/web-audio.md), and
[tests without the browser's session](doc/testing.md).


Your own controls
-----------------

For a player with controls of your own, the `controls` namespace lets
the keyboard press the same handlers as the lock screen. The keys work
only while the focus is in the player, as
[WCAG asks of shortcuts of one key](https://www.w3.org/WAI/WCAG22/Understanding/character-key-shortcuts),
so you give `listen!` the element that holds the player. A seek bar of
your own takes its
[ARIA attributes](https://www.w3.org/WAI/ARIA/apg/patterns/slider/examples/slider-seek/)
from `seek-aria`, so that a screen reader reads the position in words in
the page's language, and `pointer-seek` turns a click or a drag on it
into the seek that the lock screen's scrubber asks for:

```clojure
(require '[dk.simongray.play-nice.controls :as controls])

;; what the buttons of the lock screen do to the audio element
(def handlers
  (session/element-actions audio {:seekbackward 15 :seekforward 30}))

;; in the element that holds the player, space and k play and pause,
;; and j, l and the arrow keys skip
(controls/listen! player audio handlers)

;; in the seek bar, the arrow keys move a second, Page Up and Page Down
;; 15 seconds, and Home and End to the start and the end
(controls/listen! seek-bar audio handlers {:keymap controls/seek-bar-keys})

;; the seek bar in Hiccup, from the element's state that watch! keeps
[:div#seek-bar (merge {:aria-label "Position" :tabindex 0}
                      (controls/seek-aria (:player @app)))]
;; with e.g. {:role "slider" :aria-valuenow 754
;;            :aria-valuetext "12 minutes, 34 seconds" …}
```


Chapters and captions
---------------------

Only Chromium takes the chapters of the Media Session's metadata, but
every browser follows a
[text track](https://developer.mozilla.org/en-US/docs/Web/API/WebVTT_API)
of chapters, without showing it, and `tracks/watch!` says which chapter
plays. For a video, `tracks/show!` shows the
captions that best fit the languages you prefer. A page can't read the
system's caption settings, so `captions/style!` turns the user's own
style into [CSS for the captions](https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Selectors/::cue),
in the vocabulary of
[the FCC's rule for caption displays](https://www.ecfr.gov/current/title-47/chapter-I/subchapter-C/part-79/subpart-B/section-79.103):

```clojure
(require '[dk.simongray.play-nice.media.captions :as captions]
         '[dk.simongray.play-nice.media.tracks :as tracks])

;; the chapters of an episode as a hidden track, whose active cue is the
;; chapter that plays; without a duration, the last lasts to the end
(tracks/add! audio {:kind :chapters
                    :cues (tracks/chapter-cues [{:title "Intro" :start-time 0}
                                                {:title "News" :start-time 95}]
                                               nil)})

;; a video's captions or subtitles in Danish, or else in English
(tracks/show! video {:kinds [:captions :subtitles] :languages ["da" "en"]})

;; its captions in the user's style: larger, yellow, with a drop shadow
(captions/style! video {:font-scale 1.5 :color :yellow :edge :drop-shadow})
```


Picture-in-Picture, fullscreen and casting
------------------------------------------

For a video, `picture-in-picture/enter!` shows it in a floating window,
as [Picture-in-Picture](https://developer.mozilla.org/en-US/docs/Web/API/Picture-in-Picture_API)
has it. To cast, `remote-playback/prompt!` shows the browser's picker of
devices, e.g. a [Chromecast](https://developers.google.com/cast) or an
[AirPlay](https://www.apple.com/airplay/) receiver, through
[Remote Playback](https://developer.mozilla.org/en-US/docs/Web/API/Remote_Playback_API).
Call them in a click handler, since the browser refuses them without a
click. Each answers with data, and has a `watch!` that follows its
state.

On an iPhone, only a video element itself can go
[fullscreen](https://developer.mozilla.org/en-US/docs/Web/API/Fullscreen_API),
in the system's own player, so `fullscreen/enter!` falls back to it
there:

```clojure
(require '[dk.simongray.play-nice.display.fullscreen :as fullscreen])

(-> (fullscreen/enter! player {:video video})
    (.then prn))
;; prints {:fullscreen :element} on a desktop, {:fullscreen :video} on an
;; iPhone, or e.g. {:refused :not-allowed} without a click
```


Around the player
-----------------

Before the user's first click, a browser usually plays a page's media
only muted, if at all. `autoplay/play-when-allowed!` plays an element
muted where the browser refuses only its sound, and otherwise once more
at the user's next click, tap or key press, and
`autoplay/on-activation!` calls any function then. For a muted video at the top of a page,
`autoplay/play-in-view!` plays it while it's in view and pauses it when
it scrolls away, in every browser, and holds it where the user
[prefers reduced motion](https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/At-rules/@media/prefers-reduced-motion).

On a desktop, no browser stops one tab's audio when another starts, so
`tabs/attach!` holds a
[Web Lock](https://developer.mozilla.org/en-US/docs/Web/API/Web_Locks_API)
named for your app while the element plays, and pauses the element when
another tab takes the lock. No browser keeps the screen on for audio
either, so `wake-lock/hold-while-playing!` holds a
[Screen Wake Lock](https://developer.mozilla.org/en-US/docs/Web/API/Screen_Wake_Lock_API)
while it plays, e.g. for a transcript.


Choosing a file
---------------

A podcast episode often comes in several files, e.g. Opus and AAC. To choose
one, describe each as a configuration of
[Media Capabilities](https://developer.mozilla.org/en-US/docs/Web/API/Media_Capabilities_API),
e.g. `{:type :file :audio {:content-type "audio/mp4"}}`, give them to
`capabilities/decoding-infos!`, and keep those that `playable?` accepts.
Media Capabilities alone refuses some of the commonest files, e.g. MP3
in Firefox, so the answer also has what
[canPlayType](https://developer.mozilla.org/en-US/docs/Web/API/HTMLMediaElement/canPlayType)
says, as [the browsers' differences](doc/browsers.md#two-answers-about-what-plays)
explains.


Links, embeds and schemas
-------------------------

For a file's URL, `fragment/time` reads the time of its
[media fragment](https://www.w3.org/TR/media-frags/), e.g. `#t=73,133`,
and for a page's link, `fragment/link-time` reads its `t`, e.g.
`?t=1m13s`. With `session/clip-position`, the lock screen then shows
such a soundbite rather than the whole episode.

A page that embeds a player from another site in an
[iframe](https://developer.mozilla.org/en-US/docs/Web/HTML/Reference/Elements/iframe)
has to [allow](https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/Permissions_Policy)
what it may do, and `embed/allow` writes the attribute, e.g.
`"fullscreen; picture-in-picture"` for `{:fullscreen true
:picture-in-picture true}`.

The `schema` namespace has the shapes of the maps as
[Malli](https://github.com/metosin/malli) schemas, written as plain data,
so the library doesn't depend on Malli. Check a map with Malli of your
own, e.g. in a test: `(m/validate schema/media-effect [:seek 754])`.


With podcast-clj
----------------

A podcast app in the browser ideally uses the
[podcast-clj](https://github.com/simongray/podcast-clj) library for the feeds and
the listener's queue and progress (and play-nice for the playback).
Neither library depends on the other, but they do share the design of their data,
e.g. the data `library/playback` returns for an episode can go straight into play-nice:

```clojure
(require '[dk.simongray.podcast.app.library :as library])

(let [{:keys [metadata] :as playback-state} (library/playback @lib id)]
  (media/source! audio playback-state)
  (session/metadata! metadata))
```

The `resolve/decoding-configuration` function in [podcast-clj](https://github.com/simongray/podcast-clj)
describes a file of a feed for `capabilities/decoding-info!`,
and its `links/at-time` writes the links to a moment that `fragment/time` reads.
Its guide to [playing episodes](https://github.com/simongray/podcast-clj/blob/master/doc/building-an-app.md#play-episodes)
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
what each browser supports, and what each engine refuses.


Development
-----------

[Development](doc/development.md) has the commands that run the tests in
Node, on the JVM and in each browser.
