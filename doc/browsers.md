# The browsers' differences

The Media Session, Media Capabilities and the media element look simple,
but each engine implements its own part of them and throws where the
others don't. This page explains how play-nice deals with the
differences, and why. The research behind it, with its sources, is in
[the design of October 2026](design-2026.md), and the facts in the
[reference](reference.md).


## The position is the browser's

Without a position state from the page, Chromium, WebKit and Firefox all
follow the media element themselves. They also notice a stall, which a
page sees only through events. A position state from the page takes over
until the page clears it, and then the page must set it again at every
pause, seek and change of rate. It must also declare the playback state,
since Chromium stops the position only at a pause that the page declares.
Otherwise the lock screen most likely runs ahead after a pause, as
Chromium's source suggests.

So `attach!` leaves the position to the browser, and only declares the
playback state from the element. That's all a page with one element
needs. Its `:position-fn` takes over where the browser can't follow, e.g.
two players at once in a crossfade, since Chromium follows only a single
player, or a clip of a longer file. Shaka Player, whose Media Session
support was the most thorough of the JavaScript players we looked at,
makes the same choice.


## A new MediaMetadata for each change

The spec lets a page change the metadata object that the session holds,
and every engine then updates the lock screen. In practice it goes wrong:
Chromium detaches an object that's assigned twice, so later changes to it
are lost, and WebKit fetches the artwork again. The artwork and the
chapters are frozen arrays anyway.

So `metadata!` builds a new MediaMetadata from the map at each change.
It skips a map equal to the last, which also makes it safe to call at
every change of an app's state.


## Refused actions are data

Each engine accepts its own list of actions and throws a TypeError for
the rest: Firefox refuses those of video calls, Chrome refuses
togglescreenshare and voiceactivity, and Safari refuses hangup, the slide
actions and enterpictureinpicture.
A throw would leave the other handlers unset, and all an app can do about
a refused action is leave its button out. So `actions!` sets each
handler on its own, and returns the refused ones as a set.

No engine lists the actions it accepts, so `support` finds out by
clearing each one and noting those that don't throw. An accepted action
may still never be called, e.g. WebKit sends the lock screen's next
button to nexttrack and never to skipad.


## Safari's own buttons

Without any handlers, every engine plays, pauses and seeks the media
element itself. Safari drops all but play and pause as soon as a page
sets one handler, though, so a page that adds only track buttons loses
its scrubber and its skips. That's why `element-actions` exists: its
handlers do what the browser would, and an app merges its own over them.


## The skip seconds

The details of a skip may have seconds of the browser's own. According to
its source, WebKit offers Apple's lock screen skips of 15 seconds and
sends those 15 with each, while Chromium sends a skip from Linux's media
controls without any. So a skip uses the browser's seconds where it has
them, since its button most likely shows them, and the app's otherwise.
Ignoring the browser's seconds is an option, at the cost of a button that
may say 15 and skip 30.

Making the next and previous buttons skip is an option too, since it
gives them another meaning than their own. What a previous button does
follows Android's Media3: it goes to the item before within the first 3
seconds, and otherwise to the start of the item.


## A failed element is paused

A fatal error fires only `error` at the media element, and leaves
`paused` false. Every engine does that even for a file that won't play
at all, so a session that follows `paused` alone keeps the lock screen
on "playing". That's why `attach!` follows `error` too, and counts an
element with an error as paused, while `media/state` gives it a status
of its own.


## Refusals to play are data

The promise of `play()` rejects in three ways, and only two of them are
failures. NotAllowedError means that the page may not play yet, and
NotSupportedError that the file won't play. AbortError only means that a
pause or a new source came first. A rejection that nobody handles shows
up as an error in the console, so `play!` resolves to a map for each
instead.

Only Firefox can tell a page in advance whether it may play, so
`autoplay/policy` returns nil elsewhere. The answer of `play!` is what
works everywhere, and `on-activation!` waits for the click, tap or key
press after which the browser lets the page play.


## The source first, then the rest

Setting `src` pauses the element without a pause event, and resets the
rate to the default rate. A position set before the metadata has loaded
is where every engine starts the new source. So `source!` sets the src,
then the position, then the rate, which also makes a listener of
`loadedmetadata` unnecessary. Such a listener seeks the wrong file when
someone switches episode before the metadata arrives.


## One range of rates

Each engine accepts its own playback rates. Chrome throws outside 1/16
to 16, Firefox only below 0, and WebKit never. So `rate!` keeps every
rate within 1/16 and 16, and returns the rate that it refused, rather
than throw in one engine and play at 20 times the speed in another.


## Chapters as a track

The chapters of the metadata reach only Chromium, which shows them only
on ChromeOS. A chapters track is part of HTML instead, and every engine
fires cuechange at a hidden one, even though none shows it. So the
current chapter is the active cue of such a track.

The track comes from a track element that `add!` makes, since a track
from addTextTrack can never be removed, and would stay through every new
source. The element loads a WebVTT file without cues, since one without
a src fails to load, and the cues follow once it has loaded, since Chrome
and WebKit drop the cues added before.


## Two answers about what plays

Media Capabilities is the newer way to ask whether a browser can play a
file, but on its own it refuses some of the commonest ones. Firefox
rejects MP3, WebKit says it can't play it, and every engine refuses AAC
in MP4 without a codecs parameter, which is how most podcast feeds give
it. canPlayType, the older way, says "maybe" or "probably" to all of
them, and the files play.

So `decoding-info!` asks canPlayType too whenever Media Capabilities
says no or rejects the configuration. The answer keeps both, so an app
can still tell a confident yes from a guess, and `playable?` accepts
either. It never rejects, since an invalid configuration shouldn't stop
an app from choosing among the others.


## Clojure maps, not JavaScript objects

The maps have the spec's member names in kebab-case, and the values of
its enums as keywords. One rule converts the names both ways, so a member
that a browser adds before play-nice knows about it passes through. A nil
value is left out on the way to JavaScript, since WebIDL turns a null
string into "null", while a missing member gets its default.


## What's left out

- **Audio Session.** Only Safari ships it, and Chrome has no plans. Safari
  needs it to show a Web Audio player on the lock screen, which
  [How to put a Web Audio player on the lock screen](web-audio.md) does
  by hand.
- **Picture-in-Picture and Remote Playback.** They matter for video, and
  wait until a video app wants them.
- **Document Picture-in-Picture, Media Source Extensions and Encrypted
  Media Extensions.** They're about windows, streaming and DRM rather
  than a player's place in the system.
