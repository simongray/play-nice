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
