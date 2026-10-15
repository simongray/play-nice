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


## The back/forward cache needs nothing more

A page restored from the back/forward cache may find its video paused or
still playing, depending on the browser. But where the browser changes
the video's state, it fires the video's event, and `attach!` follows
that, so the lock screen stays right without a pageshow handler. Only a
page that declares the playback state by hand needs to check it at
pageshow.


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


## Shortcuts only in the player

A shortcut of a single key that types a character must work only while
its part of the page has the focus, unless the user can turn it off or
remap it, as WCAG 2.2 asks. Following the keys pressed in the player
meets that without a settings page. So `listen!` follows them there, and
calls the handlers of the lock screen, so that a key and a button of the
lock screen do the same.

The keys still belong to the page where it uses them itself: in a field,
on a button that the space bar presses, and on a seek bar of its own,
whose arrows move it by a second rather than skip.


## One answer for each reason to refuse Picture-in-Picture

Each engine refuses a video in its own words. A video that disables
Picture-in-Picture throws an InvalidStateError in Chrome and Firefox and
a NotSupportedError in WebKit, which throws that for other reasons too.
So `enter!` checks the video itself first, and answers with one keyword
for each reason, e.g. `:disabled` or `:no-video`, before it asks the
browser. It asks at once, without awaiting anything, since the click that
called it counts only while it's handled. Only a video without its
metadata waits for it first, as Media Chrome does.


## Caption sizes in percent

A page can't read the system's caption settings, so the user's style
has to live in the app, and `captions` turns it into the CSS of `::cue`,
the one way to style captions that the browser draws. WebVTT sets the
default size in `vh`, but in a page's CSS every engine reads `vh` as the
page's viewport, not the video's, so a caption sized in `vh` would grow
with the window. A percentage scales the browser's own size instead,
which is also how the FCC's rule puts it, from 50% to 200% of the
default. The edges of the letters are text shadows, the one way to draw
them that `::cue` allows.


## The iPhone's fullscreen is the video's

An iPhone has no fullscreen of an element, only of a video, in the
system's own player. So `enter!` tries the element first and falls back
to the video, and its answer says which went fullscreen, since a player
of your own isn't shown in the system's.

The system's player also fails in a way of its own: a video that has
never played enters it, but in the iOS simulator it can't leave it
again, most likely since iOS loads no media before a video plays. So
`exit!` answers with the state it actually left, rather than with false,
and the docs say to start the video in the tap that enters.


## Casting in two vocabularies

Remote Playback names the refusals of its picker in a way of its own:
InvalidAccessError without a click, and NotAllowedError for a picker that
the user closed. So `prompt!` answers `:not-allowed` for the first, as
`play!` and Picture-in-Picture do, and `:dismissed` for the second. The
engines don't keep to it either, e.g. Chrome sometimes says that a picker
it never showed was dismissed.

WebKit also fires its older AirPlay events, and Video.js drives its
AirPlay button from them, since it finds the standard events unreliable
in Safari. So `watch!` follows both, and the last word wins.


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
- **Document Picture-in-Picture, Media Source Extensions and Encrypted
  Media Extensions.** They're about windows, streaming and DRM rather
  than a player's place in the system.
