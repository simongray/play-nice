# Design, October 2026

This page proposes a ClojureScript library for the browser's media
integration APIs, for any app that plays audio or video: podcasts, music,
audiobooks, radio or video courses. It starts from the Media Session code
of podcast-clj (commit fdaba91) and was written on 12 October 2026.

Facts about specs and browsers were checked on the primary source unless
marked *(unverified)*. The probes ran in Chromium 152, which is the
browser pane of the Claude app. The engines' source was read on `main` on
the same day, at the commits under Sources. Nothing ran in Safari or
Firefox.

The library was named play-nice once the design was decided. It's worth
a dependency only if it does more than rename
the JavaScript, so the design is about data in and out, the sharp edges
of the specs handled once, and a session that keeps in step with a media
element. The research changed where the value lies. Every engine
already follows a lone media element by itself, so the sharp edges matter
more than we expected, and `attach!` less (section 3.4).


## 1. Scope

| Spec | Latest revision | In the library |
|---|---|---|
| [Media Session](https://www.w3.org/TR/mediasession/) | Working Draft, 5 June 2026 | Yes, the core |
| [Media Capabilities](https://www.w3.org/TR/media-capabilities/) | Working Draft, 9 June 2026 | Yes, decoding only |
| [Picture-in-Picture](https://www.w3.org/TR/picture-in-picture/) | Working Draft, 16 June 2026 | In a later batch |
| [Remote Playback API](https://www.w3.org/TR/remote-playback/) | Candidate Recommendation Draft, 30 April 2024 | In a later batch |
| [Autoplay Policy Detection](https://www.w3.org/TR/autoplay-detection/) | Working Draft, 4 September 2025 | Not until a second engine ships it |
| [Audio Session](https://www.w3.org/TR/audio-session/) | Working Draft, 13 November 2024 | Not until it ships widely |
| [Document Picture-in-Picture](https://wicg.github.io/document-picture-in-picture/) | WICG draft, 4 February 2026 | No |
| Media Source Extensions, Encrypted Media Extensions | | No |

The Media Working Group writes all of the W3C specs here, except Remote
Playback, which is the Second Screen Working Group's. On 12 October 2026,
each Editor's Draft had the same date as the latest revision. The
repositories of Audio Session and Remote Playback hold a later change
that neither draft has published yet: interruptions in place of
suspensions for an AudioContext, and a note that a Picture-in-Picture
window counts as local playback.

### 1.1 Media Session

The library covers the whole of what a player uses:

- the metadata, i.e. MediaMetadata with its artwork and chapters (§6 to
  §8);
- the actions and their details (§4.4, §5 and §10);
- the position state (§4.5 and §9);
- the playback state (§4.1).

The actions of video calls, e.g. togglemicrophone and hangup, go through
the same map of handlers as the others. The calls that report whether a
call's microphone, camera or screen share is on, e.g.
`setMicrophoneActive`, stay out, since they're for calls rather than
playback.

These details of the spec and of the browsers' source shape the design:

- Without a position state from the page, the browser follows the media
  element itself, as the spec recommends (§4.5). All three engines do:
  - Chromium does when the page has a single player
    (`RebuildAndNotifyMediaPositionChanged` in `media_session_impl.cc`);
  - WebKit starts from the element's duration, time and rate
    (`MediaElementSession::computeNowPlayingInfo`);
  - Firefox reports a guessed state from each element
    (`MediaStatusManager::GetCurrentPositionState`).

  In each, a position state from the page takes over from the element
  until the page clears it.
- The browser extrapolates the position from the last position state at
  its playback rate, or at 0 while the actual playback state is paused
  (§4.5). Chromium counts only a pause that the page declares, though
  (`RecalculatePositionState` in Blink's `media_session.cc`). So a page
  that sets the position state must set it again at each pause, seek and
  change of rate, and must declare the playback state too. Otherwise the
  position goes on running through a pause.
- The actual playback state is "playing" if the page declared it, and
  otherwise what the browser guesses from the media elements, which is
  "playing" only for an element that plays and isn't muted (§4.1). So a
  declared "paused" can't override a playing element.

### 1.2 Media Capabilities

The library uses `decodingInfo` (§2.5.2) to find out which file of an
episode or track the browser can play, e.g. for podcast-clj's
`:playable-pred` in step 10 of its later plan. It leaves out
`encodingInfo`, which is for recording and WebRTC, and the configuration
of a key system, which is EME.

On its own, Media Capabilities answers wrongly for the files that apps
meet. A container that can hold several codecs needs a codecs parameter
to be valid (§2.3.3), and podcast feeds rarely give one. In Chromium 152,
`audio/mp4` and `audio/x-m4a` without codecs come back as unsupported,
while `canPlayType` says "maybe" and the file plays. A video
configuration also requires a width, height, bitrate and frame rate
(§2.1.4), which an enclosure rarely declares. So the library asks
`canPlayType` from the HTML Standard whenever Media Capabilities says
no or rejects the configuration (D7).

### 1.3 Picture-in-Picture and Remote Playback

Both matter for video, so they come in a later batch:

- **Picture-in-Picture** puts a video in a small window of its own. The
  API is small, but its promises and events read better as data, and the
  Media Session's enterpictureinpicture action lets Chrome open it when
  the user leaves the tab: from Chrome 120 for video calls, and from
  Chrome 134 for playback, according to Chrome's blog. The
  `autoPictureInPicture` attribute of a video was taken out of the spec
  in December 2022, in
  [w3c/picture-in-picture#216](https://github.com/w3c/picture-in-picture/pull/216).
- **Remote Playback** sends an element to an AirPlay or cast device. The
  spec has hardly changed since 2017, and WebKit implements it over the
  same code as Safari's older AirPlay API, e.g.
  `webkitShowPlaybackTargetPicker`. Firefox has neither.

Document Picture-in-Picture is left out. It opens a whole page in a
small window, which is about windows rather than media, and it's a
Community Group draft that Safari doesn't have.

### 1.4 Autoplay Policy Detection and Audio Session

**Autoplay Policy Detection** gives a page `navigator.getAutoplayPolicy`,
which answers "allowed", "allowed-muted" or "disallowed" for media
elements or Web Audio. Only Firefox ships it, since version 112, so a
function over it would answer "don't know" in Chrome and Safari. Apps
call `play()` and look at how its promise fails instead.

**Audio Session** lets a page say what kind of audio it plays, e.g.
"playback" or "ambient", and follow interruptions such as a phone call.
Safari has had `type` since 16.4, and Firefox has `type`, `state` and the
statechange event in Nightly. Chrome has no plans. Dale Curtis said so
at the Media WG meeting of
[9 September 2025](https://www.w3.org/2025/09/09-mediawg-irc), and at
that of [11 August 2026](https://www.w3.org/2026/08/11-mediawg-minutes.html)
Sangwhan said Chrome would reconsider once large sites use it.

One use matters already. Safari shows a player on Web Audio on the lock
screen only when the page sets the type to "playback", and then ignores
its playback state and handlers
([w3c/mediasession#378](https://github.com/w3c/mediasession/issues/378),
opened by Mozilla in September 2026). A one-line setter could come early
if a user of the library plays through Web Audio.

### 1.5 Browser support

From MDN's [browser-compat-data](https://github.com/mdn/browser-compat-data)
8.1.5 of 8 October 2026, read from its built `data.json`, and checked
against the source files on `main` at 85c50b0 on 12 October 2026, where
none of these features had changed. A number is the first version with
support, and "no" means none up to the current Chrome 155, Firefox 157
and Safari 27. Edge follows Chrome from 79.

| Feature | Chrome | Chrome Android | Firefox | Firefox Android | Safari | Safari iOS |
|---|---|---|---|---|---|---|
| Media Session | 73 | 57 | 82 | 82 ¹ | 15 | 15 |
| MediaMetadata | 57 | 57 | 82 | 82 ¹ | 14 | 14 |
| playbackState | 73 | 57 | 82 | 82 ¹ | 15 | 15 |
| setPositionState | 81 | 57 | 82 | 82 ¹ | 15 | 15 |
| chapterInfo | 127 | 127 | no | no | no | no |
| play, pause, seekbackward, seekforward, previoustrack, nexttrack | 73 | 57 | 82 | 82 | 15 | 15 |
| stop | 77 | 77 | 82 | 82 | 15 | 15 |
| seekto | 78 | 78 | 82 | 82 | 15 | 15 |
| skipad | 128 | 128 | 82 | 82 | 15 | 15 |
| togglemicrophone, togglecamera | 93 | 93 | no | no | 18.4 | 18.4 |
| hangup | 93 | 93 | no | no | no | no |
| previousslide, nextslide | 111 | 111 | no | no | no | no |
| enterpictureinpicture | 120 | 120 | no | no | no | no |
| enterPictureInPictureReason in the details | 142 | 142 | no | no | no | no |
| Media Capabilities, decodingInfo | 66 | 66 | 63 | 63 | 13 | 13 |
| getAutoplayPolicy | no | no | 112 | 112 | no | no |
| requestPictureInPicture | 69 | 105 | 153 | no | 13.1 | 13.4 |
| Document Picture-in-Picture | 116 | no | 151 | no | no | no |
| Remote Playback | 121 | 56 | no | no | 13.1 | 13.4 |
| Audio Session, type | no | no | Nightly | no | 16.4 | 16.4 |
| Audio Session, state and statechange | no | no | Nightly | no | no | no |

¹ Firefox for Android has the API but shows no media controls for it,
as BCD notes.

Beyond the table:

- BCD has no entries for the togglescreenshare and voiceactivity
  actions. WebKit accepts both where its setting for the call actions is
  on, as in Safari, and Chromium and Firefox refuse both.
- The Android WebView has no Media Session at all, as BCD records with
  [crbug 40611412](https://crbug.com/40611412). An app in Capacitor or
  Cordova on Android needs a native plugin for its lock screen.

The newest versions in the table came out on these dates: Chrome 127 in
July 2024, Chrome 142 in October 2025, Safari 18.4 in March 2025,
Firefox 151 in May 2026 and Firefox 153 in July 2026.

### 1.6 What exists already

Nothing in Clojure or ClojureScript wraps any of these APIs as a
library. Searches of Clojars for the names of the APIs and of players
found no match beyond Web Audio libraries, which have no media
integration. The only artifact that touches them is
[cljs-web-api](https://github.com/clojure-interop/cljs-web-api) 1.0.10
of September 2019, which generates one function per member. It has five
for MediaSession and nothing for `setPositionState`, Picture-in-Picture or
Remote Playback. On GitHub, Media Session code in ClojureScript is app
code, e.g. the re-frame app [Tubo](https://github.com/migalmoreno/tubo)
and podcast-clj itself. GitHub's code search needs a login and couldn't
be used, so the search there went through Sourcegraph and the repository
search, which cover only part of GitHub.

In JavaScript, a session that follows the element lives inside players,
and [Shaka Player's](https://github.com/shaka-project/shaka-player/blob/main/ui/media_session.js)
is the one to learn from:

- It sets each handler in a try/catch of its own, and clears only the
  handlers it set.
- It sets the position state only for a live stream or a window that
  doesn't start at 0, and clears it for an ordinary file, so that the
  browser follows the element. It never sets the playback state.
- It checks for `chapterInfo` before passing chapters.
- When it stops, it removes its listeners, empties the metadata, clears
  the position state and clears its handlers.

Vidstack sets metadata, handlers and the playback state, but no
position. Video.js, Media Chrome and Plyr have little or nothing. The
standalone npm packages are small and young, and each has a gap, e.g.
`media-session` 0.1.0 turns an infinite duration into 0, which breaks a
live stream. Media Chrome's handling of Picture-in-Picture and Remote
Playback is the model for the later batch: it retries Picture-in-Picture
once the metadata has loaded, and treats a `NotSupportedError` from
`watchAvailability` as an availability it can't know until the user
asks.


## 2. The sharp edges

Each edge below is handled once in the library. Where it says Chromium
152, it was tried in the browser pane.

- **Feature detection.** Node and the Android WebView have no Media
  Session, `setPositionState` came later than the rest, and only Chromium
  has chapters. Every call checks first and does nothing where the
  browser lacks it.
- **Actions.** Every engine throws a TypeError for an action outside its
  own list, since WebIDL converts the string to the enum first, and each
  list differs:
  - Chromium has all but togglescreenshare and voiceactivity (both throw
    in Chromium 152), and threw for skipad before Chrome 128.
  - WebKit has every action of the spec. It throws for hangup,
    previousslide, nextslide and enterpictureinpicture, though, which sit
    behind a setting that's off, and for the call actions where its
    setting for them is off, as in the Linux ports.
  - Firefox has only play, pause, seekbackward, seekforward, seekto,
    previoustrack, nexttrack, skipad and stop.

  An accepted action needn't be called, e.g. WebKit sends the lock
  screen's next button to nexttrack and never to skipad. The library sets
  each handler in a try of its own (D3).
- **Default buttons.** Without handlers, each engine plays, pauses and
  seeks the element itself, e.g. WebKit with skips of 15 seconds. But
  WebKit drops all but play and pause once the page sets one handler, so
  a page that sets only the track buttons loses the scrubber and the
  skips on an iPhone. So `element-actions` gives the element's own
  handlers, to merge with the app's (section 3.3).
- **The position state.** Its throws differ by engine, from Chromium
  152, WebKit's IDL and `MediaSession.cpp`, and Firefox's IDL and
  `MediaSession.cpp`:

  | Position state | Chromium | Safari | Firefox |
  |---|---|---|---|
  | No argument | Clears | Clears | Clears |
  | `{}` | Clears | TypeError | Clears |
  | No duration, or a negative or NaN one | TypeError | TypeError | TypeError |
  | An infinite duration, for a live stream | Accepted | TypeError | TypeError |
  | A position below 0 or past the duration | TypeError | TypeError | TypeError |
  | A rate of 0 | TypeError | TypeError | TypeError |
  | A position or rate that's NaN or infinite | TypeError | TypeError | TypeError |
  | A negative rate, for playing backwards | Accepted | Accepted | Accepted |

  The spec has allowed an infinite duration since January 2024
  ([w3c/mediasession#304](https://github.com/w3c/mediasession/pull/304)),
  and the WebKit bug for it is open
  ([259642](https://bugs.webkit.org/show_bug.cgi?id=259642)). The library
  clamps the position and counts a bad rate as 1, as podcast-clj's
  `position-state` does. It clears with no argument, and clears the state
  of a live stream where the browser refuses an infinite duration, so
  that the browser follows the element.
- **A position state takes over.** Once a page sets it, the browser
  stops following the element, and in Chromium the position runs on
  through a pause that the page doesn't declare (section 1.1). So
  whatever sets it must follow every event, declare the playback state,
  and clear the state when it stops.
- **Metadata objects.** All three engines update the lock screen when
  the title, artist, album or artwork of the assigned object change, as
  §6 says. Changing it in place still goes wrong:
  - In Chromium, assigning the same object again detaches it, so later
    changes to it are lost. That's from `MediaSession::setMetadata` and
    wasn't seen on a lock screen. Assigning an empty artwork list doesn't
    clear the artwork either.
  - In WebKit, assigning the same object again fetches its artwork again.
  - The chapters are read-only, the artwork and the chapters are frozen
    arrays, and assigning a plain object throws.

  So the assumption that each change needs a new MediaMetadata holds in
  practice. The library builds a new one from the map at each change,
  and skips it when the map hasn't changed.
- **Artwork URLs.** The MediaMetadata constructor parses each `src`
  against the page's base URL and throws a TypeError for one that doesn't
  parse (§6, "convert artwork"), in all three engines. Chromium 152
  throws for `http://[bad`. A podcast's artwork URL comes from a
  stranger's feed, so podcast-clj's `apply!` can throw today and leave the
  buttons unset. The library leaves out the images that don't parse and
  sets the rest.
- **Choosing the artwork.** Each engine picks an image its own way. On
  Android, Chromium wants at least 114 pixels and ideally 512, WebKit
  wants 128 and 512 and tries an image without sizes last, and Firefox
  ignores declared sizes above 1024. The library passes the images as
  given, and the README will say to offer a square PNG of 512 by 512 with
  its sizes and type.
- **Chapters.** A browser without `chapterInfo` ignores the member, as
  WebIDL has it for any dictionary, so the library passes chapters to
  every browser. Even Chromium shows them only in ChromeOS's media
  controls, behind a feature that's off by default, so for now they're
  data. Chromium 152 throws for a start time that's NaN. The spec also
  throws for a start time that's negative "or greater than duration"
  (§7), where no duration is defined, while Chromium accepts -1. The
  library leaves out a chapter without a finite start time of 0 or more.
- **The playback state.** Assigning a value that isn't in the enum is
  ignored silently, as for any enum attribute in WebIDL. Chromium lets a
  declared "playing" win and honours "paused" only for players it can't
  control, such as Web Audio, while WebKit overwrites the state whenever
  an element plays or pauses. A page on Web Audio alone needs a declared
  state for Chromium's controls on desktop, Audio Session for Safari
  (section 1.4), and a later Firefox
  ([bug 2067534](https://bugzilla.mozilla.org/show_bug.cgi?id=2067534)).
- **Media element events.** The `durationchange` event comes before
  `loadedmetadata`, with an infinite duration for a live stream, and
  again whenever the duration changes, e.g. when an estimate is revised
  or a stream ends (HTML §4.8.11.5 and §4.8.11.6). A new source fires
  `emptied` without a `durationchange` for the reset to NaN, and the end
  of playback fires `pause` before `ended` (§4.8.11.8). So following the
  element takes more events than the obvious ones (section 3.4).
- **Media Capabilities.** It rejects a configuration with neither audio
  nor video, an invalid MIME type, or a video without its required
  members, and answers no for a container without codecs (section 1.2).
  The library never rejects, and falls back to `canPlayType`.
- **Advanced compilation.** The Closure Compiler of shadow-cljs 3.5.3,
  v20260907, has no externs for the Media Session at all, so e.g.
  `setPositionState`, `artwork` and `seekTime` are renamed unless a `^js`
  hint makes shadow-cljs write externs for them. It has externs for Media
  Capabilities, Picture-in-Picture and Remote Playback, but not for
  `getAutoplayPolicy` or Audio Session. The library hints every
  JavaScript value, reads the details by their own keys rather than by
  property names, and builds its browser tests in CI with
  `:warnings-as-errors #{:infer-warning}`, which shadow-cljs 3.5.3
  supports (`should-warning-throw?` in `shadow/build/compiler.clj`).


## 3. API

### 3.1 Namespaces

| Namespace | What it holds |
|---|---|
| `dk.simongray.play-nice.session` | The Media Session: the pure functions, the calls and `attach!` |
| `dk.simongray.play-nice.capabilities` | What the browser can play, from Media Capabilities and `canPlayType` |
| `dk.simongray.play-nice.keys` | The spec's names to keywords and back |
| `dk.simongray.play-nice.picture-in-picture` | Picture-in-Picture of a video, in a later batch |
| `dk.simongray.play-nice.remote-playback` | AirPlay and casting, in a later batch |

The library is ClojureScript alone, since nothing on the JVM has a lock
screen, and it has no dependencies.

### 3.2 Data

Everything is plain maps. The members of the spec's dictionaries become
kebab-case keywords and the values of its enums become keywords (D1 and
D2). A map goes back to JavaScript by the reverse of the same rule, so a
member that the library doesn't know yet passes through both ways, e.g.
`enterPictureInPictureReason`, which the spec added in September 2025.

The metadata, as MediaMetadataInit:

```clojure
{:title        "Episode 3 - The Future"
 :artist       "John Doe"
 :album        "Podcasting 2.0 Namespace Example"
 :artwork      [{:src "https://example.com/cover.png" :sizes "512x512"
                 :type "image/png"}]
 :chapter-info [{:title "Intro" :start-time 0}
                {:title "Talk" :start-time 94}]}
```

The details of an action, as MediaSessionActionDetails:

```clojure
{:action :seekto :seek-time 754.2 :fast-seek true}
```

The position state, as MediaPositionState:

```clojure
{:duration 3600 :playback-rate 1.5 :position 754}
```

The handlers, as a map of action to a function of the details, where a
nil function clears that action:

```clojure
{:play   (fn [details] …)
 :seekto (fn [{:keys [seek-time]}] …)}
```

### 3.3 The session

The pure functions:

- `actions`, the set of every action of the spec.
- `(position-state duration rate position)`, the position state for a
  player, or nil while the duration is unknown. It's podcast-clj's
  function, which keeps the position within the duration and counts a
  rate that isn't a finite number other than 0 as 1.
- `(element-position element)`, the position state of a media element,
  from a map of its attributes, e.g. `{:duration 3600 :current-time 754
  :playback-rate 1.5 :paused false}`.
- `(element-state position-fn element)`, what the session shows for the
  element: its playback state, and the position state of `position-fn`.
- `(seek-position details element offsets)`, where a seek action goes on
  the element, within 0 and its duration. The `offsets` give the seconds
  of seekbackward and seekforward when the details have no seek offset.
- `(metadata m image-pred)`, the metadata without the chapters whose
  start time Chromium refuses, and without the images that fail
  `image-pred`, by default those without a `:src`.

The calls, in ClojureScript:

- `(support)`, what the browser's Media Session supports, or nil where
  it has none, e.g. `{:actions #{:play :pause …} :position-state? true
  :chapter-info? false}`.
- `(metadata! metadata)`, which shows the metadata, or clears it for
  nil. It returns the metadata it set, without the artwork that didn't
  parse.
- `(actions! handlers)`, which sets exactly these handlers and clears any
  that an earlier call set. It returns the set of actions that the
  browser refused, e.g. `#{:voiceactivity}` in Chromium 152.
- `(position! state)` and `(playback-state! state)`, for a player
  without a media element, e.g. one on Web Audio, and for `attach!`.
- `(element-actions element offsets)`, handlers that play, pause, stop
  and seek the media element itself, to merge with handlers of your own.
  The skips are left out unless the `offsets` give their seconds (D10).
- `(attach! element opts)`, which keeps the session in step with the
  media element and returns a function that detaches it.

A music player with no rules of its own needs these calls:

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

### 3.4 Following a media element

Without a position state, every engine follows the media element
itself (section 1.1), and it sees a stall that a page sees only through
events. So by default `attach!` leaves the position to the browser (D9):
it keeps the position state clear, and declares the playback state from
the element (D6). For a page with one element that plays its own file,
that's all, and `attach!` adds little but a clean detach.

The option `:position-fn` takes over the position too. It's a function
from the element's attributes to the position state, e.g.
`session/element-position`, for what the browser can't follow:

- two players at once, e.g. in a crossfade, since Chromium follows only
  a single player;
- a timeline that isn't the element's, e.g. a clip of a longer file, a
  window of a live stream, or an ad.

At each of these events, `attach!` reads the element and sets what it
owns:

| Event | Why |
|---|---|
| `loadedmetadata`, `durationchange` | The duration is known or has changed |
| `play`, `playing` | Playback starts, or goes on after a stall |
| `pause` | The position must stop at the right place |
| `seeked`, `ratechange` | The position or rate jumped |
| `ended` | The position is the end |
| `emptied` | A new source, so the state clears |

It doesn't follow `timeupdate`, since the browser extrapolates the
position itself (§4.5), nor `waiting`, since `playing` corrects the
position after a stall.

An attachment owns the listeners, the playback state and, with
`:position-fn`, the position state. The metadata and the handlers stay
with `metadata!` and `actions!`, since they change with each track while
the element stays (D4). Detaching removes the listeners, clears the
position state and sets the playback state to "none". Attaching a second
element detaches the first, since a page has one session (D5). For
tests, the option `:session` takes another object to set.

### 3.5 What the browser can play

- `(decoding-info! configuration)`, a promise of what the browser says
  about a MediaDecodingConfiguration, e.g. `{:supported true :smooth true
  :power-efficient true}`. It never rejects. Where Media Capabilities is
  missing, rejects or says no, the answer has `canPlayType`'s answer for
  the content types too, the weakest of the audio's and the video's, e.g.
  `:can-play-type :maybe`.
- `(decoding-infos! configurations)`, a promise of a map of each
  configuration to its answer.
- `(can-play-type content-type)`, what `canPlayType` says: `:probably`,
  `:maybe`, or nil for no.
- `(playable? info)`, which is pure: true when the browser supports the
  configuration, or answered "probably" or "maybe" to `canPlayType`.

A predicate for podcast-clj's `resolve/enclosure` then takes one promise:

```clojure
(-> (capabilities/decoding-infos!
     (map resolve/decoding-configuration (resolve/enclosures item)))
    (.then (fn [infos]
             (resolve/enclosure
              item
              {:playable-pred (comp capabilities/playable?
                                    infos
                                    resolve/decoding-configuration)}))))
```

Here `resolve/decoding-configuration` would be podcast-clj's, a pure
function from an enclosure's type, codecs and bitrate to a configuration
map, since only podcast-clj knows enclosures.

### 3.6 Picture-in-Picture and Remote Playback

These are sketched only, for the later batch:

- `picture-in-picture/available?`, `enter!`, which returns a promise of
  the window's size, `exit!`, and `watch!`, which calls a function with
  the state at each enter, resize and leave and returns a function that
  stops it.
- `remote-playback/watch!`, which calls a function with the device's
  availability and the connection state and returns a function that
  stops it, and `prompt!`, which must run in a click handler.

With the session, a handler of enterpictureinpicture that calls
`picture-in-picture/enter!` gives automatic Picture-in-Picture in Chrome.


## 4. Open choices

| Id | Question | Recommendation | Why |
|---|---|---|---|
| D1 | Do keys keep the spec's names, e.g. `:seekTime`, or become kebab-case? | Kebab-case, by one rule both ways | It reads as Clojure, and podcast-clj's own maps are kebab-case, e.g. its chapters' `:start-time`. The names convert without a table, since no member has two capitals in a row or a digit, so a new member passes through. The cost is that MDN's examples need translating, and podcast-clj's `episode/metadata` changes two keys. |
| D2 | Are actions and other enum values strings or keywords? | Keywords, spelled as in the spec, e.g. `:seekto` and `:allowed-muted` | They're closed sets of names, the map of handlers reads best with keyword keys, and `name` and `keyword` lose nothing. podcast-clj's `actions` and `events` change from strings, which costs nothing before its first release. |
| D3 | How are unsupported actions reported? | `actions!` returns the set of refused actions and never throws, and `support` lists the accepted ones | All an app can do about an unsupported action is leave its button out, which data serves best, and a throw would leave the other handlers unset. No engine lists its actions, but each throws for one it lacks, so `support` tries each once per page with a nil handler, before the library sets any. |
| D4 | Does `attach!` own the metadata and the handlers too? | No | They change with each track while the element stays, as in podcast-clj, where the track buttons depend on the episode's chapters. Otherwise changing track means attaching again or a third function to update an attachment. The cost is three calls instead of one for a simple player. |
| D5 | Can two elements be attached at once? | No, attaching one detaches the other | A page has one session, so two attachments would overwrite each other's state. |
| D6 | Does `attach!` declare the playback state? | Yes | Chromium stops a position state from the page only at a declared pause (section 1.1). Without one, it does no harm: Chromium honours "paused" only for players it can't control, such as Web Audio, and WebKit overwrites the state from the element. |
| D7 | Does `decoding-info!` fall back to `canPlayType`? | Yes, whenever Media Capabilities says no or rejects | Otherwise the most common podcast files, AAC in MP4 without codecs, count as unplayable in Chromium (section 1.2). The answer keeps both, so an app can tell them apart. |
| D8 | Promises or callbacks? | JavaScript promises, as like-minded's `sync!` returns in ClojureScript | The browser's own calls return promises. |
| D9 | Does `attach!` set the position state by default? | No, only with `:position-fn` | Every engine follows a lone element itself, and sees a stall that the page sees only as events. A position state from the page takes over until it's cleared, so it's worth setting only where the browser can't follow. Shaka Player does the same. The alternative is to take over always, for one behaviour everywhere, as this design first assumed. |
| D10 | Do the skips of `element-actions` have a default offset? | No, a skip without one is left out | The spec gives none, and apps differ, e.g. WebKit's own skips are 15 seconds and podcast-clj's are 15 back and 30 forward. |

Decided on 12 October 2026: all as recommended, and batches 1 and 2
were built the same day.


## 5. podcast-clj

### 5.1 What moves

podcast-clj takes no dependency on the library, and an app joins the
two. From `app.media-session`:

- `position-state` moves to `session/position-state`, with its tests.
- `apply!` becomes `session/metadata!` and `session/actions!`.
- `position!` goes, since the browser follows podcast-clj's single
  element by itself. As the guide calls it, at `loadedmetadata`, `seeked`
  and `ratechange` but not at `play` or `pause`, it most likely puts
  Chromium's lock screen ahead after a pause: the position state from
  the page keeps its rate through the pause (section 1.1). That's read
  from Blink's source and wasn't tried on a lock screen.
- The browser test of the session moves to the library's browser tests,
  and the `browser-test-media-session` configuration of
  `.claude/launch.json` goes with it.

What stays is podcast-specific: `seek-actions`, `track-actions`,
`actions` and `events`, which turn the buttons into player events of
`library/step`. Since `actions` always includes seekto, Safari keeps its
scrubber. With D1 and D2 they take keywords and read `:seek-offset` and
`:seek-time`, and `episode/metadata` returns `:chapter-info` and
`:start-time`. The namespace is then pure, without reader conditionals,
and its entry in `doc/namespaces.md` loses the sentence on `apply!` and
`position!`.

### 5.2 The guide

In "Play episodes" of `building-an-app.md`, the paragraph on the Media
Session would introduce the library by name and link it, and the example
would join the two:

```clojure
(require '[dk.simongray.play-nice.session :as session])

(defn play-episode!
  "Start the episode `id` of the library in the media element `audio`."
  [audio id]
  (let [{:keys [src position rate metadata]} (library/playback @lib id)
        step!  #(apply-effects! audio (:effects (swap! lib library/step %)))
        press! (fn [details]
                 (run! step! (media-session/events
                              @lib id (.-currentTime audio) details)))]
    (set! (.-src audio) src)
    (set! (.-playbackRate audio) rate)
    (.addEventListener audio "loadedmetadata"
                       #(set! (.-currentTime audio) position)
                       #js {:once true})
    (session/metadata! metadata)
    (session/actions! (zipmap (media-session/actions @lib id)
                              (repeat press!)))
    (.play audio)))
```

The paragraph after it, which says to call `position!` at three events,
goes. This example couldn't be run, since neither change exists yet.


## 6. Tests

The pure functions run in Node, compiled both plainly and with advanced
optimizations. The tests cover:

- `position-state`, with podcast-clj's cases and a position or rate
  that's NaN or infinite;
- `element-position` before the metadata and for a live stream, and
  `element-state` with and without a position;
- `seek-position` at both ends and without an offset;
- the key rule, on every member name of the dictionaries the library
  uses, both ways;
- `actions` against the enum of §5, and `playable?`.

The browser tests run in a real page, as podcast-clj's does:

- `metadata!` with the title, artwork resolved against the page, chapters
  where the browser has them, a bad artwork URL left out, and nil.
- `actions!` and `support`, which throw nothing, with the refused set
  for the browser at hand.
- `position!` with each value of section 2 that throws somewhere.
- `attach!` on an audio element that plays a second of silence, made in
  the test as a WAV Blob so there's no fixture, and muted so it may play
  without a click. A recording object as the `:session` shows what was
  set after play, pause, a seek, a change of rate and a new source, with
  and without `:position-fn`, and that detaching removes the listeners.
- The same recording object, made to throw as WebKit's IDL does for
  `{}` and an infinite duration, so that the fallbacks for Safari run in
  every browser.
- `decoding-infos!` with `audio/mpeg`, `audio/mp4` without codecs, and an
  invalid configuration.

The same tests compile with advanced optimizations and infer warnings as
errors, which is where a missing `^js` hint shows. The browser tests have
a runner of their own for that, since the display of shadow-cljs's runner
can't be compiled with advanced optimizations. CI compiles them so, and
the runner puts the summary in the page's title, for a browser run to
read later.

Until podcast-clj's step 21 gives the siblings a browser run in CI, the
browser tests run by hand in the browser pane, with a `launch.json` like
podcast-clj's. For step 21, Playwright fits this library best, since it
drives Chromium, Firefox and WebKit, and the engines differ more here
than anywhere in the siblings. Whether Playwright's WebKit build has the
Media Session is *(unverified)*.


## 7. Batches

1. The repository as the siblings have it (`deps.edn`, `build.clj`,
   `shadow-cljs.edn`, `package.json` for the Node tests, CI on GitHub
   Actions, a README and `doc/`), and the session namespace with its
   tests. Then podcast-clj takes the move and the guide.
2. The capabilities namespace, and podcast-clj's step 10 with
   `resolve/decoding-configuration`.
3. Picture-in-Picture and Remote Playback, when a video app wants them.

Audio Session and Autoplay Policy Detection wait until a second engine
ships each, unless a player on Web Audio needs Safari's lock screen
first.


## Sources

Specs:

- Media Session, W3C Working Draft of 5 June 2026:
  <https://www.w3.org/TR/2026/WD-mediasession-20260605/>
- Media Capabilities, W3C Working Draft of 9 June 2026:
  <https://www.w3.org/TR/2026/WD-media-capabilities-20260609/>
- Picture-in-Picture, W3C Working Draft of 16 June 2026:
  <https://www.w3.org/TR/2026/WD-picture-in-picture-20260616/>
- Remote Playback API, W3C Candidate Recommendation Draft of 30 April
  2024: <https://www.w3.org/TR/2024/CRD-remote-playback-20240430/>
- Autoplay Policy Detection, W3C Working Draft of 4 September 2025:
  <https://www.w3.org/TR/2025/WD-autoplay-detection-20250904/>
- Audio Session, W3C Working Draft of 13 November 2024:
  <https://www.w3.org/TR/2024/WD-audio-session-20241113/>
- Document Picture-in-Picture, WICG Draft Community Group Report of 4
  February 2026: <https://wicg.github.io/document-picture-in-picture/>
- HTML Living Standard, §4.8.11 Media elements, as of 12 October 2026:
  <https://html.spec.whatwg.org/multipage/media.html>
- Web IDL, on the conversion of enums, dictionaries and `double`:
  <https://webidl.spec.whatwg.org/>

Browsers:

- MDN browser-compat-data 8.1.5, `api/*.json`, and `main` at 85c50b0:
  <https://github.com/mdn/browser-compat-data>
- Chromium `main` at 88f1fe85e173:
  `third_party/blink/renderer/modules/mediasession/` (`media_session.idl`,
  `media_session.cc`, `media_metadata.cc`) and
  `content/browser/media/session/media_session_impl.cc`
- WebKit `main` at 6ff73fdea8f3: `Source/WebCore/Modules/mediasession/`
  (`MediaSessionAction.idl`, `MediaPositionState.idl`, `MediaSession.cpp`),
  `Source/WebCore/html/MediaElementSession.cpp` and
  `Source/WTF/Scripts/Preferences/UnifiedWebPreferences.yaml`
- Firefox `main` at 2ef09bbf347c: `dom/webidl/MediaSession.webidl`,
  `dom/media/mediasession/`, `dom/media/mediacontrol/MediaStatusManager.cpp`
  and `dom/html/HTMLMediaElement.cpp`
- Closure Compiler v20260907, the externs in its jar
- Media WG minutes of 9 September 2025 and 11 August 2026:
  <https://www.w3.org/2025/09/09-mediawg-irc>,
  <https://www.w3.org/2026/08/11-mediawg-minutes.html>

Prior art:

- Shaka Player's `ui/media_session.js`:
  <https://github.com/shaka-project/shaka-player/blob/main/ui/media_session.js>
- Media Chrome's `state-mediator.ts`:
  <https://github.com/muxinc/media-chrome/blob/main/src/js/media-store/state-mediator.ts>
- cljs-web-api: <https://github.com/clojure-interop/cljs-web-api>
