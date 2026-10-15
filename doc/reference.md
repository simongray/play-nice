# Reference

This page describes the data that play-nice takes and returns, and what
each engine refuses. The docstrings describe the functions.


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

A chapter is a map of the keys of
[ChapterInformationInit](https://www.w3.org/TR/mediasession/#dictdef-chapterinformationinit):

| Key | Value |
|---|---|
| `:title` | A string, "" by default |
| `:start-time` | Seconds from the start, 0 by default |
| `:artwork` | A vector of images |

`metadata!` leaves out a chapter whose start time is negative, infinite
or NaN, and an image whose `:src` is missing or doesn't parse as a URL.

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

`previous-track` returns `:previous` within the first 3 seconds of an
item that has one before it, and `:restart` otherwise, as
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

`position-state` and `position!` count a rate of 0, or one that isn't
finite, as 1, keep the position within the duration, and clear the state
while the duration is unknown.

### Playback state

The playback state is `:none`, `:paused` or `:playing`, as
[MediaSessionPlaybackState](https://www.w3.org/TR/mediasession/#enumdef-mediasessionplaybackstate)
has them.

### The element map

The functions that follow a media element take a map of its attributes,
i.e. `element-position`, `element-state`, `seek-position` and the
`:position-fn` of `attach!`:

| Key | Attribute |
|---|---|
| `:duration` | [`duration`](https://html.spec.whatwg.org/multipage/media.html#dom-media-duration), NaN until the metadata has loaded |
| `:current-time` | [`currentTime`](https://html.spec.whatwg.org/multipage/media.html#dom-media-currenttime) |
| `:playback-rate` | [`playbackRate`](https://html.spec.whatwg.org/multipage/media.html#dom-media-playbackrate) |
| `:paused` | [`paused`](https://html.spec.whatwg.org/multipage/media.html#dom-media-paused) |
| `:error` | [`error`](https://html.spec.whatwg.org/multipage/media.html#dom-media-error), as a map of its `:code` and `:message`, or nil |

`attach!` reads them at the events of `session/element-events`, and
counts an element with an error as paused.

### Options

| Option | Of | Value |
|---|---|---|
| `:session` | Every function that touches the browser | The MediaSession to set, e.g. a stand-in in a test, or nil for none. The page's by default. |
| `:position-fn` | `attach!` | A function from the element map to the position state, e.g. `element-position`. Without it, the position state stays clear. |
| `:ignore-seek-offset` | `element-actions` | True to skip by the offsets even where the details have a `:seek-offset` |
| `:track-buttons` | `element-actions` | `:skips` to make `:previoustrack` skip back and `:nexttrack` forward, where the offsets give their seconds |

The offsets of `element-actions` are a map of the seconds of each skip,
e.g. `{:seekbackward 15 :seekforward 30}`.

### Support

`support` returns nil where the browser has no Media Session, and else a
map:

| Key | Value |
|---|---|
| `:actions` | The set of actions that the browser accepts |
| `:position-state?` | Whether it accepts a position state |
| `:chapter-info?` | Whether its metadata can have chapters |


## What the browser can play

The `capabilities` namespace follows the
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
| `:ended` | It has played to the end |
| `:buffering` | It isn't paused, it's loading, and it has no data past the current position, as for the [`:buffering`](https://html.spec.whatwg.org/multipage/semantics-other.html#selector-buffering) pseudo-class |
| `:idle` | It has no metadata |
| `:ready` | Otherwise |

The status doesn't say whether the element plays, since `:paused` does.

### Answers

What the promise of `play!` resolves to:

| Answer | When |
|---|---|
| `{:playing true}` | The element plays |
| `{:playing true :muted true}` | It plays muted, with `:muted-fallback`, since the browser refused its sound |
| `{:refused :not-allowed}` | The page may not play it, e.g. before the user has clicked |
| `{:refused :not-supported}` | Its source won't play |
| `{:superseded true}` | A pause or a new source came first |

`switch-source!` answers `{:paused true}` for an element that was
paused, and otherwise as `play!` does.

### Sources

The map of `source!`:

| Key | Value |
|---|---|
| `:src` | The URL. Required. |
| `:position` | Seconds from the start, where the element starts once its metadata has loaded |
| `:playback-rate` | The rate, which `rate!` sets |

### Rates

`rate!` keeps the rate within 1/16 and 16, which every engine plays, and
returns e.g. `{:playback-rate 16 :refused 20}` for a rate outside them. A
rate that isn't finite leaves the rate as it was.

### Options

| Option | Of | Value |
|---|---|---|
| `:events` | `watch!` | The events to read the state at, `media/events` by default, which leaves out `timeupdate` |
| `:muted-fallback` | `play!` | True to play muted where the browser refuses the sound |
| `:preserves-pitch` | `rate!` | False to let the pitch follow the rate |


## Text tracks

The `tracks` namespace follows the
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

`add!` takes the same keys, but `:cues` for all the cues instead of
`:active-cues`. Its `:kind` is `:subtitles` by default, and its `:mode`
`:hidden`, which follows the cues without showing them.

### Cues

| Key | Value |
|---|---|
| `:start-time` | Seconds from the start. Required. |
| `:end-time` | Seconds from the start. Required. |
| `:text` | The text, e.g. the title of a chapter |
| `:id` | A string, left out when it's empty |

`add!` leaves out the cues that the browser refuses, e.g. one whose start
time isn't finite. `chapter-cues` makes one cue of each chapter of the
session's metadata, from its `:start-time` to the next chapter's, and
from the last to the duration.

### Choosing a track

`show!` takes a map of the `:kinds` and the `:languages` that you accept,
each in the order you prefer. It shows the first track that matches,
trying each kind for a language before the next language, and turns off
the other tracks of those kinds. A language matches its regional
variants, e.g. "en" matches "en-GB". Without `:languages`, no track
matches, and all of those kinds turn off.


## Keyboard and seek bar

The `controls` namespace follows the
[UI Events](https://w3c.github.io/uievents-key/) key values, WCAG 2.2's
[character key shortcuts](https://www.w3.org/TR/WCAG22/#character-key-shortcuts),
and the APG's
[Media Seek Slider](https://www.w3.org/WAI/ARIA/apg/patterns/slider/examples/slider-seek/).

### Keys

A keymap is a map from a key, as the `key` of a KeyboardEvent names it,
to an action, or to a function from the element's state to the details
of the action. `listen!` takes `player-keys` by default:

| Keys | Action |
|---|---|
| Space, `k`, `MediaPlayPause` | `:play` while the element is paused, and `:pause` otherwise |
| `MediaPlay`, `MediaPause`, `MediaStop` | `:play`, `:pause`, `:stop` |
| `j`, `ArrowLeft`, `MediaRewind` | `:seekbackward` |
| `l`, `ArrowRight`, `MediaFastForward` | `:seekforward` |
| `MediaTrackPrevious`, `MediaTrackNext` | `:previoustrack`, `:nexttrack` |

`seek-bar-keys` holds the keys of a seek bar, as `:seekto` actions:

| Keys | Seek |
|---|---|
| `ArrowRight`, `ArrowUp` | A second forward |
| `ArrowLeft`, `ArrowDown` | A second back |
| `PageUp`, `PageDown` | 15 seconds forward or back |
| `Home`, `End` | The start or the end |

`listen!` leaves a key to the browser where the handlers have no action
for it. It also leaves alone a key with Alt, Ctrl or Meta, one that a
handler has prevented already, typing in a field, and a space that
presses a button. Holding a key down repeats only a seek.

### Seek bar

What `seek-aria` returns:

| Key | Value |
|---|---|
| `:role` | "slider" |
| `:aria-valuemin` | 0 |
| `:aria-valuemax` | The duration in whole seconds, left out while it's unknown or infinite |
| `:aria-valuenow` | The position in whole seconds |
| `:aria-valuetext` | The position in words, from `value-text` |

`value-text` writes the seconds with
[Intl.DurationFormat](https://tc39.es/proposal-intl-duration-format/), in
the locale you give or the browser's, e.g. "12 minutes, 30 seconds" or
"12 minutter og 30 sekunder", and in English where it can't.


## Picture-in-Picture

The `picture-in-picture` namespace follows the
[Picture-in-Picture](https://www.w3.org/TR/picture-in-picture/) Working
Draft of 16 June 2026.

### Answers

What the promise of `enter!` resolves to:

| Answer | When |
|---|---|
| `{:width 320 :height 180}` | The video is in Picture-in-Picture, in a window of that size |
| `{:refused :not-allowed}` | The call wasn't in a click handler, or the page may not use it, from NotAllowedError or SecurityError |
| `{:refused :not-supported}` | The browser has no Picture-in-Picture for it, e.g. for an audio element |
| `{:refused :disabled}` | The video has `disablePictureInPicture` |
| `{:refused :no-video}` | The video has no video track, e.g. it plays an audio file |

`exit!` resolves to `{:picture-in-picture false}`, and does nothing for a
video that isn't in Picture-in-Picture.

### State

What `watch!` passes on, at each enter, resize of the window and leave:

| Key | Value |
|---|---|
| `:picture-in-picture` | Whether the video is in Picture-in-Picture |
| `:width`, `:height` | The size of the window in pixels, while it's in it |


## Remote Playback

The `remote-playback` namespace follows the
[Remote Playback API](https://www.w3.org/TR/remote-playback/) Candidate
Recommendation Draft of 30 April 2024, and WebKit's AirPlay events.

### State

What `watch!` passes on:

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

`disable!` sets `disableremoteplayback` and removes `x-webkit-airplay`,
whose value "allow" lets a video on an iPhone use AirPlay all the same.


## Autoplay

The `autoplay` namespace follows the
[Autoplay Policy Detection](https://www.w3.org/TR/autoplay-detection/)
Working Draft of 4 September 2025, and
[user activation](https://html.spec.whatwg.org/multipage/interaction.html#tracking-user-activation)
in the HTML Living Standard.

`policy` returns `:allowed`, `:allowed-muted` or `:disallowed` for a
media element, an AudioContext, or the kind of either, `:mediaelement`
or `:audiocontext`. It returns nil where the browser has no
`getAutoplayPolicy`, i.e. outside Firefox.

`on-activation!` calls its function at once where the page has
transient activation, and otherwise at the first of
`autoplay/activation-events` that activates the page.

| Option | Value |
|---|---|
| `:window` | The window to ask and to listen at, e.g. a stand-in in a test. The page's by default. |


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

`position!` never passes on what throws, and clears the state where the
browser refuses an infinite duration.

### Media Capabilities

| Configuration | Chrome | Firefox | WebKit | `canPlayType` |
|---|---|---|---|---|
| `audio/mpeg` | Supported | TypeError | Not supported | Probably in Chrome, else maybe |
| `audio/mp4` without codecs | Not supported | TypeError | TypeError | Maybe |
| `audio/mp4; codecs="mp4a.40.2"` | Supported | Supported | Supported | Probably |
| `audio/ogg; codecs=opus` | Supported | Supported | Supported | Probably |
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

`add!` adds its cues once the track element has loaded.

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

Nothing was cast, since that takes a click in a picker, and would have
played on someone's device.

### Safari on iOS

Tried on 15 October 2026 in Safari on an iPhone 17 simulator with iOS
26.3, where every browser test passes:

- The volume is locked, so setting it changes nothing and fires no
  volumechange.
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
a play event. Wherever the video's state changes, it fires the event, so
`attach!` keeps the lock screen in step without a pageshow handler.


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
