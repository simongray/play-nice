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
> a library for making podcast software. Like podcast-clj, it was developed with
> assistance from frontier LLMs. It was _especially_ beneficial when I was probing for subtle browser differences.

Hard to find elsewhere
----------------------

A lot of what this library does you'd be hard pressed to find a unified API for elsewhere:

- [One playback across the tabs of an app](#tabs-and-the-screen), so that two tabs don't play at once, and each shows and controls what plays.
- [Chapters in every browser](#chapters-and-captions), as a hidden text track, since only Chromium currently accepts chapters as metadata.
- [MP3 and AAC found playable](#choosing-a-file) even when the browser API says they can't be.
- [Keyboard shortcuts that WCAG recommends](#your-own-controls), and a seek bar that a screen reader reads in the user's language.
- [Caption styles](#chapters-and-captions) in the vocabulary of the FCC's rule for caption displays.
- [Fullscreen on an iPhone](#picture-in-picture-fullscreen-and-casting), using the system's video player as nothing else may go fullscreen.
- [The iPhone's locked volume](#the-media-element), which is otherwise tricky to find out.
- [Lock screen buttons that work in Safari](#the-lock-screen), which drops its own buttons once a page sets one.


Getting started
---------------

You need a build tool that compiles ClojureScript for the browser, e.g.
[shadow-cljs](https://github.com/thheller/shadow-cljs). Add the latest
release from [Clojars](https://clojars.org/dk.simongray/play-nice) to
the `:deps` of your `deps.edn`:

```clojure
dk.simongray/play-nice {:mvn/version "0.3.0"}
```

With shadow-cljs, also set `:deps true` in your `shadow-cljs.edn`, so
that it reads the dependencies of `deps.edn`.

The [quickstart](doc/quickstart.md) takes you through a new project that
puts a track on the lock screen.
[Build a player with your own controls](doc/own-controls.md) then adds a
play button, a seek bar and keyboard shortcuts.


A podcast example
-----------------

A podcast episode should start where the listener left off, at their chosen playback rate,
and should show skip buttons on the phone's lock screen:

```clojure
(require '[dk.simongray.play-nice.media :as media]
         '[dk.simongray.play-nice.session :as session])

;; the page's <audio> element and play button
(def audio
  (js/document.querySelector "audio"))

(def play-button
  (js/document.querySelector "button"))

;; start 12:34 into the episode, at 1.5 times the speed
(media/source! audio {:src "/272.mp3" :position 754 :playback-rate 1.5})

;; show the episode on the lock screen, with buttons that skip 15 seconds
;; back and 30 forward
(session/attach! audio {:actions {:seekbackward 15 :seekforward 30}})
(session/metadata! {:title "Episode 272" :artist "My Podcast"})

(defn play-episode!
  "Play the episode, and print the browser's answer."
  [_]
  (-> (media/play! audio)
      (.then prn)))

(.addEventListener play-button "click" play-episode!)
;; prints {:playing true} at the click
```

Besides the file, `source!` sets the position and the rate in the order
that the browser needs, since a new file resets the rate. A browser
usually lets a page play sound only after a click, a tap or a key press
on it, so the example calls `play!` in the button's click handler.

> **NOTE:** the library works well on its own as seen above, but it was also designed to be paired with [podcast-clj](#with-podcast-clj).


How the API works
-----------------

A function takes the element first, and a map of options last (which you can leave out).
A function name that ends in `!` changes the page or returns a promise.
Other functions are pure.

The keys of the maps are the browser's own names in kebab-case, e.g.
`:current-time` for the element's `currentTime`. The browser's enum
strings are keywords, e.g. `:seekto` for `"seekto"`.

A browser that refuses something throws an exception or rejects a
promise. The functions return a map instead, which names the reason with
a keyword, e.g. `{:refused :not-allowed}`. Where a browser lacks a
feature, its functions do nothing or answer `{:refused :not-supported}`,
so you can call them in any browser.

The namespaces are under `dk.simongray.play-nice`:

- `media` for the media element, and the namespaces under it for its
  chapters, captions, files and links
- `session` for the lock screen
- `controls` for keyboard shortcuts and a seek bar of your own
- `display` namespaces for Picture-in-Picture, fullscreen, casting and
  keeping the screen on
- `page` namespaces for autoplay, tabs and embeds
- `schema` for Malli schemas of the maps

The [reference](doc/reference.md#namespaces) lists each of them, and
those that also run on the JVM.


The media element
-----------------

A media element is an `<audio>` or `<video>` element. The `media`
namespace has the functions that play, pause and seek it, and set its
rate and volume.

The browser keeps the element's state in many properties, e.g. `paused`
and `currentTime`, and fires an event when one of them changes. With
`watch!`, your function gets the state as a map, now and at each change.
You can then keep it with the rest of your app's state, e.g. in an atom
that your views render:

```clojure
;; the state of your app
(defonce app
  (atom {}))

(media/watch! audio #(swap! app assoc :player %)
              {:events (conj media/events :timeupdate)})
;; :player is then e.g. {:status :ready :paused false :current-time 754.2 …}
```

By default, `watch!` leaves out the `timeupdate` event, which fires
several times a second while the element plays. Add it, as here, when a
view shows the position, e.g. on a seek bar.

> **NOTE:** an iPhone ignores the volume that a page sets, since it plays every
element at the system's volume. With `volume-locked!`, you can find that
out and hide your volume slider.


The lock screen
---------------

Phones show the media that plays on their lock screen, and desktops show
it in their media controls. The browser's
[Media Session API](https://developer.mozilla.org/en-US/docs/Web/API/Media_Session_API)
lets a page set what they show and handle presses of their buttons,
including those of headphones. The `session` namespace wraps it.

In the [podcast example](#a-podcast-example), the `:actions` of
`attach!` give the buttons the handlers of `element-actions`, which
play, pause and seek the element. For a button of your own, e.g. one
that plays the next episode, merge your handler over those, and pass
them all to `actions!`:

```clojure
(def handlers
  (assoc (session/element-actions audio {:seekbackward 15 :seekforward 30})
         ;; your own code loads the episode when :episode changes
         :nexttrack (fn [_] (swap! app update :episode inc))))

;; without :actions, since actions! sets the buttons
(session/attach! audio)
(session/actions! handlers)
;; => #{}, the buttons that the browser refused
```

> **NOTE**: merge your own handlers over those of `element-actions`, rather than
set them alone:
[Safari drops its own buttons](doc/browsers.md#safaris-own-buttons) as
soon as a page sets one, so a lone `:nexttrack` would leave its lock
screen without a scrubber or skip buttons.


Your own controls
-----------------

Most apps have their own play button and seek bar rather than the
browser's. The keyboard and screen readers are then yours to handle,
and the `controls` namespace helps with both.

With `listen!`, the keyboard calls the same handlers as the lock
screen's buttons, e.g. space or k to play and pause, and j or l to skip,
as on YouTube. The keys work only while the focus is inside the element
that you give `listen!`, e.g. the one that holds the player.
[WCAG asks that of shortcuts of a single key](https://www.w3.org/WAI/WCAG22/Understanding/character-key-shortcuts),
so that they don't fire by accident, e.g. while someone dictates to the
page:

```clojure
(require '[dk.simongray.play-nice.controls :as controls])

;; the element that holds the player's controls
(def player
  (js/document.getElementById "player"))

;; the handlers of the lock screen above
(controls/listen! player audio handlers)
```

For the seek bar, use a range input, `<input type="range">`, if you can.
The browser then handles its keys, the pointer and touch, and a screen
reader on a phone can adjust it with a swipe. Give it the attributes of
`range-attributes`, whose position is in words in the page's language,
and seek at its input and change events with `range-seek`:

```clojure
(controls/range-attributes (:player @app))
;; => e.g. {:min 0 :max 3600 :step 1 :value 754
;;          :aria-valuetext "12 minutes, 34 seconds"}

(doseq [event ["input" "change"]]
  (.addEventListener seek-bar event #((:seekto handlers) (controls/range-seek %))))
```

Without the words, a screen reader would read the position as a bare
754.

A seek bar of your own needs the rest from you too. `slider-attributes`
gives it the
[ARIA attributes of a slider](https://www.w3.org/WAI/ARIA/apg/patterns/slider/examples/slider-seek/),
`slider-keys` gives `listen!` its keys, e.g. the arrows that move a
second, and `slider-seek` gives the seek at the pointer.
[Build a player with your own controls](doc/own-controls.md) builds one.


Chapters and captions
---------------------

Many podcast episodes have chapters, i.e. the titles and start times of
their parts. To show the current chapter, you could put the chapters in
the Media Session's metadata, but only Chromium accepts them there.
Every browser handles a
[text track](https://developer.mozilla.org/en-US/docs/Web/API/WebVTT_API)
of chapters, though, and marks the active chapter without showing the
track. So `tracks/add!` adds such a track to the element. Then
`tracks/watch!` calls your function with the element's tracks whenever
they change, e.g. at the start of a chapter:

```clojure
(require '[dk.simongray.play-nice.media.tracks :as tracks])

;; the chapters of a 30-minute episode, each until the next one starts
(tracks/add! audio {:kind :chapters
                    :cues (tracks/chapter-cues [{:title "Intro" :start-time 0}
                                                {:title "News" :start-time 95}]
                                               1800)})

(tracks/watch! audio #(swap! app assoc :tracks %))
;; at 2:00, :tracks is e.g.
;; [{:kind :chapters :mode :hidden …
;;   :active-cues [{:start-time 95 :end-time 1800 :text "News"}]}]
```

A video often comes with captions or subtitles in several languages, as
`<track>` elements. To show the track that suits the viewer best, give
`tracks/show!` the kinds and the languages that they accept, in the
order that they prefer them:

```clojure
;; the page's <video>, with <track> elements for its captions and subtitles
(def video
  (js/document.querySelector "video"))

;; Danish captions or subtitles, or else English ones
(tracks/show! video {:kinds [:captions :subtitles] :languages ["da" "en"]})
```

Phones and computers let users choose the size and colours of captions,
e.g. larger letters, but a page can't read those settings. So you have
to offer settings of your own, and `captions/style!` turns the user's
choices into
[CSS for the video's captions](https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Selectors/::cue).
The keys of the style are those of
[the FCC's rule for caption displays](https://www.ecfr.gov/current/title-47/chapter-I/subchapter-C/part-79/subpart-B/section-79.103),
which lists the caption settings that a video device in the US must
offer:

```clojure
(require '[dk.simongray.play-nice.media.captions :as captions])

;; captions at 150%, in yellow, with a drop shadow
(captions/style! video {:font-scale 1.5 :color :yellow :edge :drop-shadow})
```


Picture-in-Picture, fullscreen and casting
------------------------------------------

Besides playing in the page, a video can play in a floating window above
other apps, fullscreen, or on a TV:

- [Picture-in-Picture](https://developer.mozilla.org/en-US/docs/Web/API/Picture-in-Picture_API)
  with `picture-in-picture/enter!`
- [fullscreen](https://developer.mozilla.org/en-US/docs/Web/API/Fullscreen_API)
  with `fullscreen/enter!`
- [casting](https://developer.mozilla.org/en-US/docs/Web/API/Remote_Playback_API)
  with `remote-playback/prompt!`, which shows the browser's list of
  devices, e.g. a [Chromecast](https://developers.google.com/cast) or an
  [AirPlay](https://www.apple.com/airplay/) receiver

Browsers allow each of them only in the handler of a click. Each
namespace also has a `watch!` that follows the state, e.g. to notice
when the user closes the floating window.

On an iPhone, a page can't put an element fullscreen, only a video,
which then plays in the system's own player. So `fullscreen/enter!`
falls back to the video there. Your own controls don't show in the
system's player, so the answer says which of the two went fullscreen:

```clojure
(require '[dk.simongray.play-nice.display.fullscreen :as fullscreen])

;; in a click handler, where player holds the video and your controls
(-> (fullscreen/enter! player {:video video})
    (.then prn))
;; prints {:fullscreen :element} on a desktop, where your controls show,
;; or {:fullscreen :video} on an iPhone, where they don't
```


Autoplay
--------

Each browser has its own rules for autoplay, which
[MDN's guide to autoplay](https://developer.mozilla.org/en-US/docs/Web/Media/Guides/Autoplay)
explains. To start as soon as the browser allows, call
`autoplay/play-when-allowed!`. It plays the element at once where the
browser allows it, and muted where the browser refuses only the sound.
Otherwise it plays the element at the user's next click, tap or key
press anywhere on the page. That suits e.g. a video that should start
when the page opens.

For a muted video at the top of a page, `autoplay/play-in-view!` plays
it while it's in view and pauses it when it scrolls away. It doesn't
start the video at all where the user
[prefers reduced motion](https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/At-rules/@media/prefers-reduced-motion),
a system setting for people who are bothered by movement on screen.


Tabs and the screen
-------------------

On a desktop, a listener can open your app in two tabs and start an
episode in each. No browser stops one tab's audio when another tab
starts, so both play at once and each overwrites the position that the
other saved. With `tabs/attach!`, only one tab plays at a time, through
a [Web Lock](https://developer.mozilla.org/en-US/docs/Web/API/Web_Locks_API)
named for your app:

```clojure
(require '[dk.simongray.play-nice.page.tabs :as tabs])

(tabs/attach! audio "my-podcast-app")
```

It also tells the other tabs what plays, where and how fast, so that the
player of every tab can show the same playback, and the controls of any
tab can act on it:

```clojure
(tabs/attach! audio "my-podcast-app"
              {:id-fn    #(:episode @app)   ; what the element plays
               :handlers handlers           ; for the actions of other tabs
               :align?   true})             ; start from where the playback is

(tabs/watch! "my-podcast-app" #(swap! app assoc :playback %))

(tabs/press! "my-podcast-app" {:action :pause})  ; e.g. from another tab
```

The playback that `watch!` gives moves on in a tab that doesn't play, as
`tabs/position` works it out, and only the tab that plays tells where it
is, so that a tab that only shows it never saves its position.

A playing video keeps the screen on, but audio doesn't, so a phone's
screen goes dark while an episode plays, even when the page shows its
transcript. With `wake-lock/hold-while-playing!`, the screen stays on
while the element plays, through a
[Screen Wake Lock](https://developer.mozilla.org/en-US/docs/Web/API/Screen_Wake_Lock_API).


Choosing a file
---------------

A podcast episode often comes in several files, e.g. Opus and AAC. To
choose one that the browser can play, describe each as a configuration of
[Media Capabilities](https://developer.mozilla.org/en-US/docs/Web/API/Media_Capabilities_API),
the browser's API for whether it can play a format, and how well. On its
own, _Media Capabilities_ at times refuses some very common files. Firefox and
Safari refuse MP3, and every browser refuses AAC when its type names no
codecs, as most podcast feeds give it. So
`capabilities/decoding-info!` asks the older
[canPlayType](https://developer.mozilla.org/en-US/docs/Web/API/HTMLMediaElement/canPlayType)
too, and `playable?` accepts the file where either says it plays:

```clojure
(require '[dk.simongray.play-nice.media.capabilities :as capabilities])

;; AAC, with the type that most podcast feeds give it
(-> (capabilities/decoding-info! {:type  :file
                                  :audio {:content-type "audio/mp4"}})
    (.then prn))
;; prints {:supported false :smooth false :power-efficient false
;;         :can-play-type :maybe}
```


Links, embeds and schemas
-------------------------

A link can point at a moment of an episode, e.g. to share a soundbite.
A link to the file does that with a
[media fragment](https://www.w3.org/TR/media-frags/) at the end of its
URL, e.g. `#t=73,133` for the minute from 1:13, and the browser starts
the file there. A link to a page does it with a `t` parameter, e.g.
`?t=1m13s`, as YouTube's links do. To read the times, use
`fragment/time` and `fragment/link-time`:

```clojure
(require '[dk.simongray.play-nice.media.fragment :as fragment])

(fragment/time "https://cdn.example/272.mp3#t=73,133")
;; => {:start 73 :end 133}

(fragment/link-time "https://example.com/episodes/272?t=1m13s")
;; => 73
```

Other sites may embed your player in an
[iframe](https://developer.mozilla.org/en-US/docs/Web/HTML/Reference/Elements/iframe).
In its `allow` attribute, the iframe has to
[permit](https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/Permissions_Policy)
the features that the player uses, or e.g. fullscreen won't work in it.
To build the attribute from a map, use `embed/allow`, e.g.
`(embed/allow {:fullscreen true :picture-in-picture true})` returns
`"fullscreen; picture-in-picture"`.

The `schema` namespace describes the maps of the library as
[Malli](https://github.com/metosin/malli) schemas. They're plain data,
so play-nice doesn't depend on Malli. With Malli in your own app, you
can check a map, e.g. in a test:
`(m/validate schema/media-effect [:seek 754])`.


With podcast-clj
----------------

A podcast app in the browser ideally uses the
[podcast-clj](https://github.com/simongray/podcast-clj) library for the feeds and
the listener's queue and progress (and play-nice for the playback).
Neither library depends on the other, but they do share the design of their data,
e.g. the data `library/playback` returns for an episode can go straight into play-nice:

```clojure
(require '[dk.simongray.podcast.app.library :as library])

;; lib is an atom of podcast-clj's library, and id the id of an episode
(let [{:keys [metadata] :as playback-state} (library/playback @lib id)]
  (media/source! audio playback-state)
  (session/metadata! metadata))
```

The `resolve/decoding-configuration` function in [podcast-clj](https://github.com/simongray/podcast-clj)
describes a file of a feed for `capabilities/decoding-info!`,
and its `links/at-time` makes the links to a moment that `fragment/time` parses.
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

The library supports Chrome, Edge, Firefox and Safari. Its tests run in
Chrome, Firefox and WebKit, and in Safari on an iPhone simulator. Edge
runs on Chrome's engine, so the tests in Chrome cover it too.

The Android WebView has no Media Session, so an Android app that shows
your page in a WebView, e.g. through Capacitor or Cordova, needs a
native plugin for its lock screen. The
[reference](doc/reference.md#browser-support) lists what each browser
supports, and what each engine refuses.


Development
-----------

[Development](doc/development.md) has the commands that run the tests in
Node, on the JVM and in each browser.
