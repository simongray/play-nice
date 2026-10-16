# Reference

This page describes the data that play-nice takes and returns, and what
each engine refuses. The docstrings, e.g. on
[cljdoc](https://cljdoc.org/d/dk.simongray/play-nice), describe the
functions.


## Namespaces

The namespaces are under `dk.simongray.play-nice`:

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
their functions, only `captions/style!`, `controls/listen!` and
`controls/pointer-seek` need a browser.


## Keys and values

The keys of every map are the members of the spec's dictionaries in
kebab-case, e.g. `:seek-time` for `seekTime` and `:chapter-info` for
`chapterInfo`. A map goes back to JavaScript by the same rule in reverse,
so a member that play-nice doesn't know yet passes through both ways.
Nil values are left out on the way to JavaScript, which gives the member
its default.

The values of the spec's enums are keywords, spelled as the spec spells
them, e.g. `:seekto` and `:media-source`.

The maps of a media element have its attributes as keys, by the same
rule, e.g. `:current-time` for `currentTime`. Its numbered states are
keywords named after their constants, e.g. `:have-metadata` for
`HAVE_METADATA`.


## The media element

The `media` namespace follows the
[media elements](https://html.spec.whatwg.org/multipage/media.html)
section of the HTML Living Standard.

### State

What `state` returns, and `watch!` passes on:

| Key | Value |
|---|---|
| `:status` | The status below |
| `:paused`, `:ended`, `:seeking`, `:muted` | Booleans |
| `:ready-state` | `:have-nothing`, `:have-metadata`, `:have-current-data`, `:have-future-data` or `:have-enough-data` |
| `:network-state` | `:empty`, `:idle`, `:loading` or `:no-source` |
| `:current-time` | Seconds from the start |
| `:duration` | Seconds, nil until the metadata has loaded, or `##Inf` for a live stream |
| `:playback-rate` | E.g. 1.5 |
| `:volume` | From 0 to 1 |
| `:buffered`, `:seekable`, `:played` | Vectors of `[start end]` in seconds |
| `:error` | Nil, or a map of the `:code` and the browser's `:message` |

The code of an error is `:aborted`, `:network`, `:decode` or
`:src-not-supported`, as
[MediaError](https://html.spec.whatwg.org/multipage/media.html#mediaerror)
numbers them.

### Status

The status is the first of these that applies:

| Status | When |
|---|---|
| `:error` | The element has an error |
| `:ended` | It's at the end |
| `:buffering` | It isn't paused, it's loading, and it has no data past the current position, as for the [`:buffering`](https://html.spec.whatwg.org/multipage/semantics-other.html#selector-buffering) pseudo-class |
| `:idle` | It has no metadata |
| `:ready` | Otherwise |

The status doesn't say whether the element plays, since `:paused` does.
Nor does `:ended` say that it played to the end, since a seek to the end
while it's paused ends it too. To start the next track, listen for the
element's `ended` event, which fires only when it played there.

### Answers

What the promise of `play!` resolves to:

| Answer | When |
|---|---|
| `{:playing true}` | The element plays |
| `{:playing true :muted true}` | It plays muted, with `:muted-fallback`, since the browser refused its sound |
| `{:refused :not-allowed}` | The page may not play it, e.g. before the user has clicked |
| `{:refused :not-supported}` | Its source won't play |
| `{:superseded true}` | A pause or a new source came first |

For an element that was paused, `switch-source!` answers
`{:paused true}`, and otherwise it answers as `play!` does.

### Effects

What `apply!` does for each effect, and its answer:

| Effect | Does | Answer |
|---|---|---|
| `[:play opts]` | `play!` | As `play!` |
| `[:pause]` | `pause!` | `{:paused true}` |
| `[:seek position]` | `seek!` | E.g. `{:current-time 754}`, or `{:current-time 12 :refused :not-finite}` for a position that isn't finite, where the element would throw |
| `[:rate x opts]` | `rate!` | As `rate!` |
| `[:volume level opts]` | `volume!` | As `volume!` |
| `[:muted muted?]` | `muted!` | E.g. `{:muted true}` |
| `[:source m]` | `source!` | As `source!` |
| `[:switch-source src]` | `switch-source!` | As `switch-source!` |
| Any other | Nothing | `{:refused :unknown-effect}` |

The answers come in the order of the effects. An effect that throws
answers as a refusal, e.g. `{:refused :type}` for a TypeError, so the
promise never rejects.

### Sources

The map of `source!`:

| Key | Value |
|---|---|
| `:src` | The URL. Required. |
| `:position` | Seconds from the start, where the element starts once its metadata has loaded |
| `:playback-rate` | The rate, which `rate!` sets |

### Rates

The rate that `rate!` sets stays within 1/16 and 16, which every engine
plays. Where it had to change the rate, the answer has the rate you
asked for as `:requested`, e.g. `{:playback-rate 16 :requested 20}`. A
rate that isn't a finite number leaves the rate as it was, and answers
e.g. `{:playback-rate 1.5 :refused :not-finite}`. Both `volume!` and
`seek!` answer in the same way, so `:refused` is always a keyword that
names the reason.

### Volume

From the level of a volume slider, from 0 to 1, `volume!` sets the
volume on one of `curves`:

| Curve | Volume of a level | Level of a volume |
|---|---|---|
| `:linear` | The level | The volume |
| `:cubic` | The level cubed, e.g. 0.125 for 0.5 | The cube root |

For the volume of an element, or of its state, `level` returns the
slider's level on the `:curve` you give it. So a view that renders the
state needs no element. Where the browser locks the volume, as an iPhone
does, an element plays at the system's volume, although its `volume`
returns what the page set, as HTML allows. There, `volume-locked!`
resolves to true, since it waits for a `volumechange` that a locked
volume doesn't fire.

### Options

| Option | Of | Value |
|---|---|---|
| `:events` | `watch!` | The events to read the state at, as keywords, `media/events` by default, which leaves out `:timeupdate` |
| `:muted-fallback` | `play!` | True to play muted where the browser refuses the sound |
| `:preserves-pitch` | `rate!` | False to let the pitch follow the rate |
| `:curve` | `volume!`, `level` | One of `curves`, `:linear` by default |
| `:fast` | `seek!` | True to seek with `fastSeek` where the browser has it, which may land near the position, sooner |


## Text tracks

The `media.tracks` namespace follows the
[text tracks](https://html.spec.whatwg.org/multipage/media.html#timed-text-tracks)
of the HTML Living Standard, and the
[VTTCue](https://www.w3.org/TR/webvtt1/#the-vttcue-interface) of the
WebVTT Candidate Recommendation Draft of 20 May 2026.

### Tracks

What `text-tracks` returns for each track, and `watch!` passes on:

| Key | Value |
|---|---|
| `:kind` | `:subtitles`, `:captions`, `:descriptions`, `:chapters` or `:metadata` |
| `:label` | A string, "" by default |
| `:language` | A BCP 47 tag, e.g. "en-GB", "" by default |
| `:mode` | `:disabled`, `:hidden` or `:showing` |
| `:active-cues` | A vector of the cues that are active now, none while the track is disabled |

The map of `add!` has the same keys, but `:cues` with all the cues
instead of `:active-cues`. Its `:kind` is `:subtitles` by default, and
its `:mode` is `:hidden`, which follows the cues without showing them.

### Cues

| Key | Value |
|---|---|
| `:start-time` | Seconds from the start. Required. |
| `:end-time` | Seconds from the start, or `##Inf` to the end of the media. Required. |
| `:text` | The text, e.g. the title of a chapter |
| `:id` | A string, left out when it's empty |

The function `add!` leaves out the cues that the browser refuses, e.g.
one whose start time isn't finite. It writes an end of `##Inf` as the
largest number, since Firefox refuses an infinite end, and `cues` reads
that back as `##Inf`. For the chapters of the session's metadata,
`chapter-cues` makes one cue each, from its `:start-time` to the next
chapter's. The last one lasts to the duration, or to `##Inf` while the
duration is unknown.

### Choosing a track

To choose a track, `show!` takes a map of the `:kinds` and the
`:languages` that you accept, each in the order that you prefer. It
shows the first track that matches, and turns off the other tracks of
those kinds. It tries each kind for a language before the next language.
A language matches its regional variants, e.g. "en" matches "en-GB".
Without `:languages`, no track matches, and all tracks of those kinds
turn off. With a `:label`, only a track of that label matches, in any
language unless `:languages` narrow it, e.g. a track that you added with
`add!`.


## Caption styles

The `media.captions` namespace follows the settings of
[47 CFR 79.103(c)](https://www.ecfr.gov/current/title-47/chapter-I/subchapter-C/part-79/subpart-B/section-79.103),
the FCC's rule for caption displays, and the `::cue` pseudo-element of
[WebVTT](https://www.w3.org/TR/webvtt1/#the-cue-pseudo-element).

### Style

A style is a map of these keys. Each is optional, and a key left out
keeps the browser's own setting:

| Key | Value | Of the rule |
|---|---|---|
| `:font-scale` | The size as a share of the browser's own, e.g. 1.5 | (c)(4), 50% to 200% |
| `:font` | `:default`, `:monospaced-serif`, `:proportional-serif`, `:monospaced-sans-serif`, `:proportional-sans-serif`, `:casual`, `:cursive` or `:small-capitals` | (c)(5), the fonts of CEA-708 |
| `:color` | `:white`, `:black`, `:red`, `:green`, `:blue`, `:yellow`, `:magenta` or `:cyan`, or a hex code, e.g. "#ffcc00" | (c)(2) |
| `:opacity` | From 0 to 1, e.g. 1 for opaque and 0.5 for semi-transparent | (c)(3) |
| `:background-color` | As `:color`, black by default | (c)(6) |
| `:background-opacity` | From 0 to 1, 0.8 by default, as WebVTT has it | (c)(6) |
| `:edge` | `:none`, `:raised`, `:depressed`, `:uniform` or `:drop-shadow` | (c)(7) |
| `:edge-color` | As `:color`, black by default | |

The FCC rule's caption window, with its colour and opacity, has no
counterpart in `::cue`.

The function `css` returns the style as CSS, e.g.
`video::cue{font-size:150%}`. To style one video, `style!` adds that CSS
to the page, and returns a function that removes it.


## What the browser can play

The `media.capabilities` namespace follows the
[Media Capabilities](https://www.w3.org/TR/media-capabilities/) Working
Draft of 9 June 2026.

### Configurations

A configuration is a map of the keys of
[MediaDecodingConfiguration](https://www.w3.org/TR/media-capabilities/#dictdef-mediadecodingconfiguration),
with `:audio`, `:video` or both:

| Key | Value |
|---|---|
| `:type` | `:file`, `:media-source` or `:webrtc`. Required. |
| `:audio` | An audio configuration |
| `:video` | A video configuration |

An audio configuration is a map of the keys of
[AudioConfiguration](https://www.w3.org/TR/media-capabilities/#dictdef-audioconfiguration):

| Key | Value |
|---|---|
| `:content-type` | The MIME type. Required. |
| `:channels` | A string, e.g. "2" |
| `:bitrate` | Bits per second |
| `:samplerate` | Samples per second |
| `:spatial-rendering` | Whether the audio is spatial |

A video configuration is a map of the keys of
[VideoConfiguration](https://www.w3.org/TR/media-capabilities/#dictdef-videoconfiguration).
Every engine rejects one without its content type, width, height,
bitrate and frame rate:

| Key | Value |
|---|---|
| `:content-type` | The MIME type |
| `:width`, `:height` | Pixels |
| `:bitrate` | Bits per second |
| `:framerate` | Frames per second |
| `:has-alpha-channel` | Whether it's transparent |
| `:hdr-metadata-type` | `:smpteSt2086`, `:smpteSt2094-10` or `:smpteSt2094-40` |
| `:color-gamut` | `:srgb`, `:p3` or `:rec2020` |
| `:transfer-function` | `:srgb`, `:pq` or `:hlg` |
| `:spatial-scalability` | Whether it uses spatial scalability |

A content type is
[valid](https://www.w3.org/TR/media-capabilities/#check-mime-type-validity)
when a type that implies its codec, e.g. `audio/mpeg`, has no parameters,
and a container type, e.g. `audio/mp4`, has one codecs parameter that
names a single codec, e.g. `audio/mp4; codecs="mp4a.40.2"`. An audio
configuration has an `audio/` type.

### Answers

What `decoding-info!` returns:

| Key | Value |
|---|---|
| `:supported`, `:smooth`, `:power-efficient` | Booleans from [MediaCapabilitiesInfo](https://www.w3.org/TR/media-capabilities/#dictdef-mediacapabilitiesinfo), when Media Capabilities answered |
| `:can-play-type` | The answer of [`canPlayType`](https://html.spec.whatwg.org/multipage/media.html#dom-navigator-canplaytype) for the content types, `:probably`, `:maybe` or nil, unless Media Capabilities supports the configuration |

For a configuration of both audio and video, `:can-play-type` is the
weaker of the two answers.

For a content type in `type-aliases`, `decoding-info!` asks by the name
that the engines know for the format, since they refuse the alias:

| Alias | Asked as |
|---|---|
| `audio/opus` | `audio/ogg; codecs="opus"`, since an `.opus` file is Ogg |
| `audio/m4a` | `audio/mp4` |
| `audio/mpeg3`, `audio/x-mp3` | `audio/mpeg` |


## Times in links

The `media.fragment` namespace follows
[Media Fragments URI 1.0 (basic)](https://www.w3.org/TR/media-frags/),
a W3C Recommendation of 25 September 2012.

For a URL, `time` returns the interval of its temporal fragment, i.e. of
its last valid `t`, as a map of the `:start` and `:end` in seconds, e.g.
`{:start 73 :end 133}` for `#t=npt:73,133`. The start is 0 where the
fragment leaves it out, and the map has no `:end` where the fragment has
none. The times are in Normal Play Time, which `npt` parses:

| Form | Example | Seconds |
|---|---|---|
| Seconds | `73.5` | 73.5 |
| Minutes and seconds | `01:13` | 73 |
| Hours, minutes and seconds | `1:02:03.5` | 3723.5 |

Minutes and seconds take two digits each. The SMPTE and clock formats of
the advanced profile count as invalid.

For the URL of a page, `link-time` returns the seconds of its `t` query
parameter, which may take the forms `73`, `73s`, `1m13s`, `1h2m3s` and
`01:13`.

For the lock screen, `session/clip-position` returns the position state
within a clip. It takes a map of the clip's `:start` and `:end`, e.g.
from `time`, and [the element map](#the-element-map). Without an `:end`,
the clip ends with the file.


## The session

The `session` namespace follows the
[Media Session](https://www.w3.org/TR/mediasession/) Working Draft of 5
June 2026.

### Metadata

Metadata is a map of the keys of
[MediaMetadataInit](https://www.w3.org/TR/mediasession/#dictdef-mediametadatainit):

| Key | Value |
|---|---|
| `:title` | A string, "" by default |
| `:artist` | A string, "" by default |
| `:album` | A string, "" by default |
| `:artwork` | A vector of images |
| `:chapter-info` | A vector of chapters, which only Chromium shows |

An image is a map of the keys of
[MediaImage](https://www.w3.org/TR/mediasession/#dictdef-mediaimage):

| Key | Value |
|---|---|
| `:src` | The URL, resolved against the page's base URL. Required. |
| `:sizes` | E.g. "512x512", "" by default |
| `:type` | E.g. "image/png", "" by default |

Each engine picks an image its own way. Chrome on Android skips those
below 114 pixels and Safari those below 128, and both prefer 512. So
offer a square PNG of 512 by 512, with its `:sizes` and `:type`.

A chapter is a map of the keys of
[ChapterInformationInit](https://www.w3.org/TR/mediasession/#dictdef-chapterinformationinit):

| Key | Value |
|---|---|
| `:title` | A string, "" by default |
| `:start-time` | Seconds from the start, 0 by default |
| `:artwork` | A vector of images |

Before it sets the metadata, `metadata!` leaves out a chapter whose
start time is negative, infinite or NaN, and an image whose `:src` is
missing or doesn't parse as a URL.

### Actions

The actions are the keywords of
[MediaSessionAction](https://www.w3.org/TR/mediasession/#enumdef-mediasessionaction),
which `session/actions` holds:

| Kind | Actions |
|---|---|
| Playing | `:play` `:pause` `:stop` |
| Seeking | `:seekbackward` `:seekforward` `:seekto` |
| Tracks | `:previoustrack` `:nexttrack` `:skipad` |
| Slides | `:previousslide` `:nextslide` |
| Calls | `:togglemicrophone` `:togglecamera` `:togglescreenshare` `:hangup` `:voiceactivity` |
| Windows | `:enterpictureinpicture` |

The handlers are a map of action to a function of the details. A nil
function clears its action.

For the previous button, `previous-track` returns `:previous` within the
first 3 seconds of an item that has an item before it, and `:restart`
otherwise, as
[Media3](https://developer.android.com/reference/androidx/media3/common/Player#seekToPrevious())
does.

### Details

A handler gets the details as a map of the keys of
[MediaSessionActionDetails](https://www.w3.org/TR/mediasession/#dictdef-mediasessionactiondetails):

| Key | For | Value |
|---|---|---|
| `:action` | Every action | The action |
| `:seek-offset` | `:seekbackward`, `:seekforward` | Seconds, when the browser gives them |
| `:seek-time` | `:seekto` | Seconds from the start |
| `:fast-seek` | `:seekto` | True when a fast, less precise seek will do, e.g. while the user drags the scrubber |
| `:is-activating` | `:togglemicrophone`, `:togglecamera`, `:togglescreenshare` | Whether the user turns it on |
| `:enter-picture-in-picture-reason` | `:enterpictureinpicture` | `:useraction`, `:contentoccluded` or `:other` |

### Position state

The position state is a map of the keys of
[MediaPositionState](https://www.w3.org/TR/mediasession/#dictdef-mediapositionstate):

| Key | Value |
|---|---|
| `:duration` | Seconds, or `##Inf` for a live stream. Required. |
| `:playback-rate` | 1 by default. A negative rate plays backwards, and 0 isn't allowed. |
| `:position` | Seconds from the start, 0 by default, within 0 and the duration |

Both `position-state` and `position!` count a rate of 0, or one that
isn't finite, as 1. They keep the position within the duration, and
clear the state while the duration is unknown.

### Playback state

The playback state is `:none`, `:paused` or `:playing`, as
[MediaSessionPlaybackState](https://www.w3.org/TR/mediasession/#enumdef-mediasessionplaybackstate)
has them.

### The element map

Some functions take the state of a media element, as `media/state`
returns it: `element-position`, `element-state`, `seek-position` and the
`:position-fn` of `attach!`. They read these keys:

| Key | Attribute |
|---|---|
| `:duration` | [`duration`](https://html.spec.whatwg.org/multipage/media.html#dom-media-duration), nil until the metadata has loaded |
| `:current-time` | [`currentTime`](https://html.spec.whatwg.org/multipage/media.html#dom-media-currenttime) |
| `:playback-rate` | [`playbackRate`](https://html.spec.whatwg.org/multipage/media.html#dom-media-playbackrate) |
| `:paused` | [`paused`](https://html.spec.whatwg.org/multipage/media.html#dom-media-paused) |
| `:error` | [`error`](https://html.spec.whatwg.org/multipage/media.html#dom-media-error), as a map of its `:code` and `:message`, or nil |

At each of `session/element-events`, `attach!` reads the state, and it
counts an element with an error as paused.

### Options

| Option | Of | Value |
|---|---|---|
| `:session` | Every function that touches the browser | The MediaSession to set, e.g. a stand-in in a test, or nil for none. The page's by default. |
| `:position-fn` | `attach!` | A function from the element's state, as `media/state` gives it, to the position state, e.g. `element-position`. Without it, the position state stays clear. |
| `:events` | `attach!` | The events of the element to follow, `session/element-events` by default |
| `:actions` | `attach!` | The options of `element-actions`, e.g. `{:seekbackward 15 :seekforward 30}`, to set its handlers too, which detaching clears |
| `:seekbackward`, `:seekforward` | `element-actions` | The seconds of each skip, e.g. 15 and 30. A skip without them is left out. |
| `:ignore-seek-offset` | `element-actions` | True to skip by those seconds even where the details have a `:seek-offset` |
| `:track-buttons` | `element-actions` | `:skips` to make `:previoustrack` skip back and `:nexttrack` forward, where the skips have their seconds |

The `:stop` handler of `element-actions` pauses the element where it
is, since Media3's `Player.stop` keeps the position too. Each of its
handlers applies what `action-effects` returns for the press: effects of
`media/apply!` as data, e.g. `[[:seek 784]]` for a skip of 30 seconds
forward at 754, or `[]` for a press that doesn't touch the element.

### Support

Where the browser has no Media Session, `support!` returns nil, and
otherwise a map:

| Key | Value |
|---|---|
| `:actions` | The set of actions that the browser accepts |
| `:position-state?` | Whether it accepts a position state |
| `:chapter-info?` | Whether its metadata can have chapters |


## Keyboard and seek bar

The `controls` namespace follows the
[UI Events](https://w3c.github.io/uievents-key/) key values, WCAG 2.2's
[character key shortcuts](https://www.w3.org/TR/WCAG22/#character-key-shortcuts),
and the APG's
[Media Seek Slider](https://www.w3.org/WAI/ARIA/apg/patterns/slider/examples/slider-seek/).

### Keys

A keymap is a map from a key, as the `key` of a KeyboardEvent names it,
to an action. Instead of an action, a key may map to a function from the
element's state to the details of an action. An action may be one of
your own, e.g. `{"f" :fullscreen}` with a `:fullscreen` handler. Unless
you give it a `:keymap`, `listen!` uses `player-keys`:

| Keys | Action |
|---|---|
| Space, `k`, `MediaPlayPause` | `:play` while the element is paused, and `:pause` otherwise |
| `MediaPlay`, `MediaPause`, `MediaStop` | `:play`, `:pause`, `:stop` |
| `j`, `ArrowLeft`, `MediaRewind` | `:seekbackward` |
| `l`, `ArrowRight`, `MediaFastForward` | `:seekforward` |
| `MediaTrackPrevious`, `MediaTrackNext` | `:previoustrack`, `:nexttrack` |

For a seek bar, `seek-bar-keys` maps these keys to `:seekto` actions:

| Keys | Seek |
|---|---|
| `ArrowRight`, `ArrowUp` | A second forward |
| `ArrowLeft`, `ArrowDown` | A second back |
| `PageUp`, `PageDown` | 15 seconds forward or back |
| `Home`, `End` | The start or the end |

Where the handlers have no action for a key, `listen!` leaves the key
to the browser. It also leaves alone a key with Alt, Ctrl or Meta, one
that a handler has prevented already, typing in a field, and a space
that presses a button. Holding a key down repeats only a seek. Since
`listen!` keeps the handlers it was given, stop it and call it again
when they change.

### Seek bar

What `seek-aria` returns:

| Key | Value |
|---|---|
| `:role` | "slider" |
| `:aria-valuemin` | 0 |
| `:aria-valuemax` | The duration in whole seconds, left out while it's unknown or infinite |
| `:aria-valuenow` | The position in whole seconds |
| `:aria-valuetext` | The position in words, from `value-text` |

The words come from `value-text`, which writes the seconds with
[Intl.DurationFormat](https://tc39.es/proposal-intl-duration-format/),
e.g. "12 minutes, 30 seconds" or "12 minutter og 30 sekunder". Its
language is the locale you give, or else that of the page, from the
`lang` of its `html` element, or else the browser's. Where there's no
Intl.DurationFormat, as on the JVM, the words are English.

For a pointer event on the seek bar that listens for it, `pointer-seek`
returns the details of a seek to that point, e.g.
`{:action :seekto :seek-time 1830}`. So a click on the bar can call the
same handler as the lock screen's scrubber. A point past either end of
the bar seeks to that end. While the duration is unknown or infinite,
`pointer-seek` returns nil.


## Picture-in-Picture

The `display.picture-in-picture` namespace follows the
[Picture-in-Picture](https://www.w3.org/TR/picture-in-picture/) Working
Draft of 16 June 2026.

### Answers

What the promise of `enter!` resolves to:

| Answer | When |
|---|---|
| `{:picture-in-picture true :width 320 :height 180}` | The video is in Picture-in-Picture, in a window of that size |
| `{:refused :not-allowed}` | The call wasn't in a click handler, or the page may not use it, from NotAllowedError or SecurityError |
| `{:refused :not-supported}` | The browser has no Picture-in-Picture for it, e.g. for an audio element |
| `{:refused :disabled}` | The video has `disablePictureInPicture` |
| `{:refused :no-video}` | The video has no video track, e.g. it plays an audio file |

The promise of `exit!` resolves to `{:picture-in-picture false}`, also
for a video that wasn't in Picture-in-Picture.

### State

What `watch!` passes on as the video enters Picture-in-Picture, as the
window resizes and as the video leaves, and what `state` returns,
without the size:

| Key | Value |
|---|---|
| `:picture-in-picture` | Whether the video is in Picture-in-Picture |
| `:width`, `:height` | The size of the window in pixels, while it's in it |


## Fullscreen

The `display.fullscreen` namespace follows the
[Fullscreen](https://fullscreen.spec.whatwg.org/) Living Standard,
WebKit's own fullscreen of a video, and the
[Screen Orientation](https://www.w3.org/TR/screen-orientation/) Working
Draft of 6 August 2026.

### State

What `state` returns, `watch!` passes on and `exit!` answers:

| `:fullscreen` | When |
|---|---|
| `:element` | The element is fullscreen |
| `:video` | Its video is in the system's own player, e.g. on an iPhone |
| `false` | Neither |

### Answers

What the promise of `enter!` resolves to:

| Answer | When |
|---|---|
| `{:fullscreen :element}` | The element is fullscreen |
| `{:fullscreen :video}` | The browser can show only the video fullscreen, e.g. on an iPhone |
| `{:refused :not-allowed}` | The call wasn't in a click handler, or the page may not, e.g. in an iframe whose `allow` leaves out `fullscreen` |
| `{:refused :no-metadata}` | On an iPhone, the video has no metadata yet |
| `{:refused :not-supported}` | Neither can go fullscreen |

With `:orientation`, e.g. `:landscape`, the answer also holds the answer
of `lock-orientation!` as `:orientation`: `{:locked :landscape}`, or a
refusal, e.g. `{:refused :not-supported}` on a desktop.

### Options

| Option | Of | Value |
|---|---|---|
| `:video` | Every function | The video element of the player, for the iPhone's fallback |
| `:orientation` | `enter!` | The orientation to lock the screen in, e.g. `:landscape` or `:portrait` |


## Remote Playback

The `display.remote-playback` namespace follows the
[Remote Playback API](https://www.w3.org/TR/remote-playback/) Candidate
Recommendation Draft of 30 April 2024, and WebKit's AirPlay events.

### State

What `watch!` passes on and `state` returns. Only `watch!` learns
whether a device is available, so `state` answers `:unknown` where the
browser has Remote Playback:

| Key | Value |
|---|---|
| `:availability` | `:available` or `:unavailable`, as the browser says, and `:unknown` until it has said, or where it looks for devices only in its picker |
| `:state` | `:disconnected`, `:connecting` or `:connected` |

### Answers

What the promise of `prompt!` resolves to:

| Answer | When | From |
|---|---|---|
| `{:state :connecting}` | The user picked a device, and the state is as `state` returns it | |
| `{:refused :not-allowed}` | The call wasn't in a click handler | InvalidAccessError |
| `{:refused :dismissed}` | The user closed the picker | NotAllowedError |
| `{:refused :no-devices}` | There's no device to pick | NotFoundError |
| `{:refused :disabled}` | The element has `disableremoteplayback` | InvalidStateError |
| `{:refused :not-supported}` | The browser has no Remote Playback, e.g. Firefox | NotSupportedError |

To keep an element off remote devices, `disable!` sets
`disableremoteplayback`. It also removes `x-webkit-airplay`, since its
value "allow" lets a video on an iPhone use AirPlay all the same.


## The screen kept on

The `display.wake-lock` namespace follows the
[Screen Wake Lock API](https://www.w3.org/TR/screen-wake-lock/). While
the element plays and its page is visible, `hold-while-playing!` holds a
lock of the screen, and it lets go of the lock at a pause. The browser
lets go of every lock while the page is hidden, so the function requests
the lock again at each `visibilitychange` that shows the page. Where the
browser refuses the lock, e.g. on a low battery, the screen goes dark as
it would have, without an error.

| Option | Value |
|---|---|
| `:window` | The window whose navigator holds the lock, the page's by default |


## Autoplay

The `page.autoplay` namespace follows the
[Autoplay Policy Detection](https://www.w3.org/TR/autoplay-detection/)
Working Draft of 4 September 2025, and
[user activation](https://html.spec.whatwg.org/multipage/interaction.html#tracking-user-activation)
in the HTML Living Standard.

For a media element, an AudioContext, or the kind of either
(`:mediaelement` or `:audiocontext`), `policy` returns `:allowed`,
`:allowed-muted` or `:disallowed`. Outside Firefox, the browser has no
`getAutoplayPolicy`, and `policy` returns nil.

Where the page has transient activation, `on-activation!` calls its
function at once, and otherwise at the first of
`autoplay/activation-events` that activates the page.

To play a media element, `play-when-allowed!` calls `media/play!`. Where
the browser refuses, it tries once more at the next activation. It calls
its function with each answer, and with
`{:refused :not-allowed :waiting true}` while it waits. Since it tries
only twice, a browser that never lets the page play can't make it loop.

| Option | Value |
|---|---|
| `:window` | The window to ask and to listen at, e.g. a stand-in in a test. The page's by default. |
| `:muted-fallback` | Of `play-when-allowed!`: false to wait for the user rather than play muted. True by default. |


## Autoplay in view

In `page.autoplay`, `play-in-view!` follows the
[Intersection Observer](https://www.w3.org/TR/intersection-observer/)
Working Draft.

What `play-in-view!` passes on:

| Key | Value |
|---|---|
| `:visible` | Whether at least the threshold of the video shows |
| `:playing` | Whether the video plays |
| `:held` | `:visitor` once the visitor has paused it, or `:reduced-motion` where the user prefers reduced motion, and left out otherwise |
| `:refused` | The refusal of the last play, e.g. `:not-allowed`, and left out otherwise |

| Option | Value |
|---|---|
| `:threshold` | The share of the video that must show for it to play, 0.5 by default |
| `:reduced-motion` | Whether to hold the video, by default as `reduced-motion?` says the user prefers |

The video plays as with `media/play!` and `:muted-fallback`, and pauses
out of view only while it's muted. A video that ends doesn't count as
paused by the visitor.


## One player across tabs

The `page.tabs` namespace follows the
[Web Locks API](https://www.w3.org/TR/web-locks/). When the element
plays, `attach!` requests the lock of the name you give, with `steal`,
and when another tab takes the lock, it pauses the element. It lets go
of the lock when the element pauses and at `pagehide`, and takes it
again when the page comes back from the back/forward cache still
playing. In a window that fires no events, e.g. Node's global object,
which has Web Locks from Node 24.5, it doesn't follow `pagehide` and
`pageshow`.

| Option | Value |
|---|---|
| `:window` | The window whose navigator holds the locks, the page's by default |


## Embedding

The `page.embed` namespace follows the
[Permissions Policy](https://www.w3.org/TR/permissions-policy-1/)
Working Draft of 22 September 2026.

From a map of each feature to who may use it in the frame, `allow`
builds the iframe's `allow` attribute:

| Value | In the attribute | Who |
|---|---|---|
| `true` | `fullscreen` | The frame's own origin, i.e. that of its `src` |
| `"*"` | `fullscreen *` | Any origin the frame goes to |
| `:none` | `fullscreen 'none'` | No one |
| `["https://player.example"]` | `fullscreen https://player.example` | Those origins |
| `false` or `nil` | | Left out, so the feature's default applies |


## Schemas

The `schema` namespace has a Malli schema for each of these maps:

| Schema | The map |
|---|---|
| `media-state` | What `media/state` returns, with `ranges` and `media-error` |
| `play-answer` | What `media/play!` answers |
| `source` | What `media/source!` loads |
| `media-effect` | An effect of `media/apply!` |
| `metadata` | What `session/metadata!` shows, with `image` |
| `position-state` | What `session/position!` shows |
| `action-details` | What a handler of the Media Session gets |
| `caption-style` | What `captions/css` takes |
| `interval` | What `fragment/time` returns |
| `fullscreen-state`, `picture-in-picture-state`, `remote-playback-state` | What the `watch!` of each passes on |

The schemas name predicates as symbols, e.g. `'number?`, which Malli's
default registry reads in Clojure and ClojureScript alike.


## What each engine refuses

Tried on 12 October 2026 in Chrome 153, Firefox 157 and WebKit 27.2,
through Playwright on macOS, unless the table says otherwise.

### Actions

Each engine throws a TypeError for an action that it lacks, and play-nice
returns those actions from `actions!`:

| Engine | Refused actions |
|---|---|
| Chrome | `:togglescreenshare` `:voiceactivity`, and `:skipad` before Chrome 128 |
| Firefox | `:togglemicrophone` `:togglecamera` `:togglescreenshare` `:hangup` `:previousslide` `:nextslide` `:enterpictureinpicture` `:voiceactivity` |
| WebKit | `:hangup` `:previousslide` `:nextslide` `:enterpictureinpicture`, and in the Linux ports also the call actions, according to its source |

An accepted action may still never be called, e.g. WebKit sends the lock
screen's next button to `:nexttrack` and never to `:skipad`.

### Position states

| Position state | Chrome | Firefox | WebKit |
|---|---|---|---|
| None, to clear it | Clears | Clears | Clears |
| An empty one, `{}` | Clears | Clears | TypeError |
| No duration, or a negative or NaN one | TypeError | TypeError | TypeError |
| An infinite duration | Accepted | TypeError | TypeError |
| A position below 0 or past the duration | TypeError | TypeError | TypeError |
| A rate of 0 | TypeError | TypeError | TypeError |
| A position or rate that's NaN or infinite | TypeError | TypeError | TypeError |
| A negative rate | Accepted | Accepted | Accepted |

Where the browser refuses an infinite duration, `position!` clears the
state, and it never passes on anything else that throws.

### Media Capabilities

| Configuration | Chrome | Firefox | WebKit | `canPlayType` |
|---|---|---|---|---|
| `audio/mpeg` | Supported | TypeError | Not supported | Probably in Chrome, else maybe |
| `audio/mp4` without codecs | Not supported | TypeError | TypeError | Maybe |
| `audio/mp4; codecs="mp4a.40.2"` | Supported | Supported | Supported | Probably |
| `audio/ogg; codecs=opus` | Supported | Supported | Supported | Probably |
| `audio/opus`, `audio/m4a` | Not supported | Not supported | Not supported | No |
| `audio/mpeg3` | Not supported | TypeError | Not supported | Maybe in WebKit, else no |
| `audio/x-mp3` | Supported | Not supported | Not supported | Probably in Chrome, maybe in WebKit, no in Firefox |
| `audio/flac` | Supported | Supported | Not supported | Probably in Chrome, else maybe |
| A video without its size | TypeError | TypeError | TypeError | Probably for H.264 |

### Metadata

| | Chrome | Firefox | WebKit |
|---|---|---|---|
| Chapters | Accepted, shown only in ChromeOS's media controls | Ignored | Ignored |
| An artwork URL that doesn't parse | TypeError | TypeError | TypeError |

### Media elements

Tried on 15 October 2026, in the same versions. In all three, an element
whose file won't play keeps `paused` false after the error, which is why
`attach!` follows `error`. Firefox rejects the promise of `play()` before
it fires `error`.

All three start a new source at a position set right after its `src`,
which `source!` relies on. Their playback rates differ, and `rate!` keeps
every rate within 1/16 and 16:

| Playback rate | Chrome | Firefox | WebKit |
|---|---|---|---|
| 0, or 1/16 to 16 | Accepted | Accepted | Accepted |
| Above 0 and below 1/16, or above 16 | NotSupportedError | Accepted | Accepted |
| Below 0 | NotSupportedError | NotSupportedError | Accepted |
| A default rate outside 1/16 to 16 | Ignored | Kept within 1/16 and 16, or an exception below 0 | Accepted |

### Text tracks

| | Chrome | Firefox | WebKit |
|---|---|---|---|
| cuechange on a hidden track | Fires | Fires | Fires |
| Cues added to a track element before it has loaded | Lost at the load | Kept | Lost at the load |
| A track element without a src | Fails to load, but takes cues | Fails to load, but takes cues | Fails to load, but takes cues |
| The last cue, at the end of the media | Stays active | Ends | Ends |
| The active cues after a seek | Changed by seeked | Changed by seeked | Changed by seeked, but later just after the track has loaded |
| Removing the track element | Removes the track | Removes the track | Removes the track |

So `add!` adds its cues once the track element has loaded.

### Picture-in-Picture

Tried on 15 October 2026 through Playwright, with a video of a canvas's
stream:

| | Chrome | Firefox | WebKit |
|---|---|---|---|
| Before the metadata | InvalidStateError | InvalidStateError | InvalidStateError |
| A video without a video track | InvalidStateError | InvalidStateError | InvalidStateError |
| A video with `disablePictureInPicture` | InvalidStateError | InvalidStateError | NotSupportedError |
| Without a click | NotAllowedError | Enters, early in a page's life, and NotAllowedError later | NotSupportedError at a page's first request, and NotAllowedError after, from a WebKit without Picture-in-Picture |
| `exitPictureInPicture` with no video in it | InvalidStateError | InvalidStateError | InvalidStateError |
| leavepictureinpicture | Not seen | After `exitPictureInPicture` has resolved | Not seen |

Playwright's WebKit has no Picture-in-Picture at all, since it can't show
the window, even with a click. With a real click on the same day, in the
browsers themselves, `enter!` put the video in Picture-in-Picture in each,
and `watch!` followed it in and out:

| | Chrome 153 | Firefox 158 | Safari 26.3 |
|---|---|---|---|
| The click | Through the accessibility API | Through the accessibility API | By hand |
| The window | 311 by 175 | 320 by 180 | 360 by 202 |

The iOS simulator has no Picture-in-Picture either: it refuses every video
with NotSupportedError, although an iPhone has it.

### Caption styles

Rendered on 15 October 2026 in a caption of a video, and measured on
screenshots, in Chrome 153 on macOS and in Playwright's Firefox and
WebKit:

| Through `::cue` | Chrome | Firefox | WebKit |
|---|---|---|---|
| `color`, `font-size` in percent, `text-shadow`, `outline` | Applied | Applied | Applied |
| `background-color` | Ignored, even with `!important` | Applied | Applied |
| `font-variant: small-caps` | Ignored | Applied | Applied |
| A `font-size` in `vh` | Relative to the page's viewport, not the video's | Likewise | Likewise |

Chrome takes the background of a caption only through
`::-webkit-media-text-track-display`, which covers the whole band of the
captions across the video rather than the box behind the text.

### Fullscreen

Tried on 15 October 2026 through Playwright, and in the iOS simulator:

| | Chrome | Firefox | WebKit | Safari on iOS |
|---|---|---|---|---|
| `requestFullscreen` of an element | Yes | Yes | Yes | No |
| `webkitEnterFullscreen` of a video | No | No | Yes | Yes |
| Without a click | TypeError, or sometimes enters soon after leaving | TypeError, or sometimes enters soon after leaving | TypeError, or enters early in a page's life | InvalidStateError, thrown |
| `screen.orientation.lock` outside fullscreen | NotSupportedError | SecurityError | None | None |

With a tap in the iOS simulator, `enter!` showed the video in the
system's player, with `webkitbeginfullscreen`. A video that hadn't played
then couldn't leave it: `webkitExitFullscreen` changed nothing, and no
`webkitendfullscreen` came. A video that started playing in the same tap
left it at once, with `webkitendfullscreen`.

### Remote Playback

Tried on 15 October 2026 through Playwright, and in the iOS simulator, on
a network with an AirPlay device:

| | Chrome | Firefox | WebKit | Safari on iOS |
|---|---|---|---|---|
| `remote` | Yes | No | Yes | Yes |
| The availability | Unavailable | – | Unavailable, then available | Unavailable |
| `prompt()` without a click | InvalidAccessError, or sometimes NotAllowedError | – | InvalidAccessError, or NotSupportedError without a source | InvalidAccessError |
| `prompt()` with `disableremoteplayback` | InvalidStateError | – | InvalidStateError | InvalidStateError |
| WebKit's AirPlay events | No | No | Yes | Yes |

### Media fragments

A file with `#t=0.5`, over HTTP or as a blob URL, tried on 15 October
2026 through Playwright, and in the iOS simulator:

| | Chrome | Firefox | WebKit | Safari on iOS |
|---|---|---|---|---|
| Starts at the fragment's start | By `loadedmetadata` | By `loadedmetadata` | After `loadedmetadata`, with `seeked` | Once it plays |

### Embedding

What an iframe from another origin may do, tried on 15 October 2026
through Playwright with Chrome 155, in Firefox 158 and Safari 26.3, and
in the iOS simulator:

| `allow` | Chrome | Firefox | WebKit and Safari | Safari on iOS |
|---|---|---|---|---|
| None | No fullscreen | No fullscreen | No fullscreen | No fullscreen of an element anyway |
| `fullscreen`, `fullscreen *` or `allowfullscreen` | Fullscreen | Fullscreen | Fullscreen | – |
| `fullscreen 'none'` with `allowfullscreen` | No fullscreen | No fullscreen | No fullscreen | – |
| `picture-in-picture 'none'` | No Picture-in-Picture | No Picture-in-Picture | Picture-in-Picture all the same | – |
| `media-playback-while-not-visible 'none'` | Pauses the frame's media, muted or not, once the frame has `display: none`, `visibility: hidden` or no size, though not once it's off screen, and leaves it paused | Plays on | Plays on | Not tried |

### Safari on iOS

Tried on 15 October 2026 in Safari on an iPhone 17 simulator with iOS
26.3, where every browser test passes:

- The volume is locked: `volume` returns what the page set, but no
  volumechange fires, and the element plays at the system's volume.
- An element that hasn't played doesn't seek: seeked waits until it
  plays, although `currentTime` returns the new position at once. So a
  hidden track's active cues don't change either.
- A seek to 0.5 seconds of a WAV file lands at about 0.511.

### The back/forward cache

Tried on 15 October 2026 with a page that plays a muted video, declares
"playing" and goes to another page and back, in the browsers themselves:

| | Chrome 153 | Firefox 158 | Safari 26.3 | Safari on iOS 26.3 |
|---|---|---|---|---|
| The page | Restored from the cache | Restored | Restored | Restored |
| The video afterwards | Paused, with a pause event after pageshow | Still playing, without events | Still playing, without events | Still playing, without events |
| The metadata and the declared playback state | Kept | Kept | Kept | Kept |

Under Playwright, Chrome played the video again after the restore, with
a play event.


## Browser support

From MDN's [browser-compat-data](https://github.com/mdn/browser-compat-data)
8.1.5 of 8 October 2026. A number is the first version with support, and
"no" means none up to Chrome 155, Firefox 157 and Safari 27. Edge follows
Chrome from 79, and the Android WebView has no Media Session at all.

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
| Media Capabilities, decodingInfo | 66 | 66 | 63 | 63 | 13 | 13 |

¹ Firefox for Android has the API but shows no media controls for it.
