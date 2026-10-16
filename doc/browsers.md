# The browsers' differences

The browser's media APIs look simple, but each engine (Chromium, Gecko
or WebKit) implements its own part of them, and throws where the others
don't. This page explains how play-nice deals with the differences, and
why. The research behind it, with its sources, is in
[the design of October 2026](design-2026.md). The facts are in the
[reference](reference.md).


## The position is the browser's

The lock screen's scrubber needs the position, the duration and the
rate, which the Media Session calls the position state. Without one from
the page, Chromium, WebKit and Firefox all take it from the media
element themselves. They also notice a stall, which a page sees only
through events. Once a page sets a position state, it takes over until
the page clears it. The page must then set it again at every pause, seek
and change of rate. It must also declare the playback state, since
Chromium stops the position only at a pause that the page declares.
Otherwise, the lock screen most likely runs on after a pause, as
Chromium's source suggests.

So `attach!` leaves the position to the browser, and only declares the
playback state from the element. That's all a page with one element
needs. Its `:position-fn` takes over where the browser can't follow,
e.g. two players at once in a crossfade, since Chromium follows only a
single player, or a clip of a longer file.


## A new MediaMetadata for each change

The spec lets a page change the metadata object that the session holds,
and every engine then updates the lock screen. In practice, it goes
wrong: Chromium detaches an object that's assigned twice, so later
changes to it are lost, and WebKit fetches the artwork again. The
artwork and the chapters are frozen arrays anyway.

So `metadata!` builds a new MediaMetadata from the map at each change.
It skips a map equal to the last, so you can call it at every change of
your app's state.


## Refused actions are data

Each engine accepts its own list of actions, and throws a TypeError for
the rest. Firefox refuses those of video calls, Chrome refuses
togglescreenshare and voiceactivity, and Safari refuses hangup, the
slide actions and enterpictureinpicture. A throw would leave the other
handlers unset, and all an app can do about a refused action is leave
its button out. So `actions!` sets each handler on its own, and returns
the refused ones as a set.

No engine lists the actions it accepts, so `support!` finds out by
clearing each one and noting those that don't throw.


## Safari's own buttons

Without any handlers, every engine plays, pauses and seeks the media
element itself. As soon as a page sets one handler, though, Safari drops
all its buttons but play and pause. So a page that adds only track
buttons loses its scrubber and its skips. That's why `element-actions`
exists: its handlers do what the browser would, and you merge your own
over them.


## The skip seconds

When the browser sends a skip, its details may name the seconds to
skip. According to its source, WebKit offers Apple's lock screen skips
of 15 seconds, and sends those 15 with each. Chromium sends a skip from
Linux's media controls without any. So a skip goes by the browser's
seconds where it sends them, since its button most likely shows them,
and by the app's otherwise. The option to ignore the browser's seconds
costs a button that may say 15 and skip 30.

Making the next and previous buttons skip is only an option too, since
it gives them a meaning other than their own. What a previous button
does follows Android's Media3: it goes to the item before within the
first 3 seconds, and otherwise to the start of the item.


## The back/forward cache needs nothing more

When the user goes to another page and back, the browser may restore
the page as it was, from its back/forward cache. The page's video may
then be paused or still playing, depending on the browser. But where the
browser changes the video's state, it fires the video's event, and
`attach!` follows that, so the lock screen stays right without a
pageshow handler. Only a page that declares the playback state by hand
needs to check it at pageshow.


## A failed element is paused

A fatal error fires only `error` at the media element, and leaves
`paused` false. Every engine does that, even for a file that won't play
at all. A session that follows `paused` alone then keeps the lock screen
on "playing". That's why `attach!` follows `error` too, and counts an
element with an error as paused. For the same reason, `media/state`
gives such an element a status of its own, `:error`.


## Refusals to play are data

The promise of `play()` rejects in three ways, and only two of them are
failures. NotAllowedError means that the page may not play yet, and
NotSupportedError that the file won't play. AbortError only means that a
pause or a new source came first. A rejection that nobody handles shows
up as an error in the console, so `play!` resolves to a map for each
instead.

Only Firefox can tell a page in advance whether it may play, so
`autoplay/policy` returns nil elsewhere. What works everywhere is the
answer of `play!`. Then `on-activation!` waits for the click, tap or key
press after which the browser lets the page play. After it,
`play-when-allowed!` tries only once more: a page may have activation
and still not be allowed to play, e.g. by Safari's Never Auto-Play
setting, and would otherwise ask again without end.


## The source first, then the rest

Setting `src` pauses the element without a pause event, and resets the
rate to the default rate. Every engine starts the new source at a
position set before its metadata has loaded. So `source!` sets the src,
then the position, then the rate. That way, it needs no listener of
`loadedmetadata` to seek, which would seek the wrong file when someone
switches episode before the metadata arrives.


## One range of rates

Each engine accepts its own playback rates. Chrome throws outside 1/16
to 16, Firefox only below 0, and WebKit never. So `rate!` keeps every
rate within 1/16 and 16, and returns the rate you asked for where it had
to change it. The alternative is a rate that throws in one engine and
plays at 20 times the speed in another.


## Chapters as a track

Chapters in the Media Session's metadata reach only Chromium, which
shows them only on ChromeOS. A chapters track is part of HTML instead,
and every engine fires cuechange at a hidden one, even though none shows
it. So the current chapter is the active cue of such a track.

The track comes from a `<track>` element that `add!` makes, since a
track from addTextTrack can never be removed, and would stay through
every new source. The element loads a WebVTT file without cues, since an
element without a src fails to load. The cues follow once it has loaded,
since Chrome and WebKit drop the cues added before.


## Shortcuts only in the player

WCAG 2.2 asks that a shortcut of a single character key works only while
its part of the page has the focus, unless the user can turn it off or
remap it. Following the keys pressed in the player meets that without a
settings page. So `listen!` follows them there, and calls the handlers
of the lock screen, so that a key does the same as the lock screen's
button.

The keys still belong to the page where it uses them itself: in a field,
on a button that the space bar presses, and on a seek bar, whose arrow
keys move it by a second rather than skip.


## One answer for each reason to refuse Picture-in-Picture

Each engine refuses a video in its own words. For a video that disables
Picture-in-Picture, Chrome and Firefox throw an InvalidStateError and
WebKit a NotSupportedError, which it throws for other reasons too. So
`enter!` checks the video itself before it asks the browser, and answers
with one keyword for each reason, e.g. `:disabled` or `:no-video`. Then
it asks at once, without awaiting anything, since the click that called
it counts only while it's handled. Only a video without its metadata
waits for it first.


## Caption sizes in percent

A page can't read the system's caption settings, so you keep the user's
style in your app, and `captions` turns it into the CSS of `::cue`. It's
the one way to style the captions that the browser draws. WebVTT sets
the default size in `vh`. In a page's CSS, though, every engine reads
`vh` as the page's viewport, not the video's, so a caption sized in `vh`
would grow with the window. A percentage scales the browser's own size
instead, which is also how the FCC's rule puts it: from 50% to 200% of
the default. The edges of the letters are text shadows, since
`text-shadow` is the one property of `::cue` that every engine draws
around the letters.


## Autoplay in view, as the engines do it

By their autoplay policies, Chrome and WebKit on iOS pause a muted video
with the `autoplay` attribute when it scrolls out of view, and play it
again when it returns. They leave alone a video that the page plays
itself, though. So `play-in-view!` follows the same rule for any video,
in every engine. It pauses only a muted video, as they do, so that a
video with sound plays on below the fold. It also gives way to the
visitor: a pause that `play-in-view!` didn't make is the visitor's, and
after one, `play-in-view!` neither plays nor pauses the video again.


## One player across tabs, by a lock

On a desktop, two tabs of the same app can play at once, and race to
save the position. A Web Lock of the app's name, taken with `steal`,
tells the tab that held it that another tab has started, so that it can
pause. Each tab's `attach!` lets go of the lock at every pause, and at
`pagehide`, since a held lock keeps a page out of the back/forward
cache.


## The iPhone's fullscreen is the video's

An iPhone can't put an element fullscreen, only a video, in the system's
own player. So `enter!` tries the element first and falls back to the
video. Its answer says which went fullscreen, since your own controls
don't show in the system's player.

The system's player also fails in a way of its own. A video that has
never played enters it, but in the iOS simulator it can't leave it
again, most likely because iOS loads no media before a video plays. So
`exit!` answers with the state that it actually leaves behind, rather
than with false, and its docstring says to start the video in the tap
that enters.


## Casting in two vocabularies

Remote Playback names the refusals of its picker in its own way:
InvalidAccessError without a click, and NotAllowedError for a picker
that the user closed. So `prompt!` answers `:not-allowed` for the first,
as `play!` and Picture-in-Picture do, and `:dismissed` for the second.
The engines don't keep to the spec either, e.g. Chrome sometimes says
that the user dismissed a picker that it never showed.

WebKit also fires its older AirPlay events, and Video.js drives its
AirPlay button from them, since it finds the standard events unreliable
in Safari. So `watch!` follows both, and whichever comes last wins.


## Two answers about what plays

Media Capabilities is the newer way to ask whether a browser can play a
file, but on its own, it refuses some of the commonest ones. Firefox
rejects MP3 and WebKit says it can't play it. Every engine refuses AAC
in MP4 without a codecs parameter, which is how most podcast feeds give
it. The older canPlayType says "maybe" or "probably" to all of them, and
the files play.

So `decoding-info!` asks canPlayType too whenever Media Capabilities
says no or rejects the configuration. The answer keeps both, so that you
can still tell a confident yes from a guess, and `playable?` accepts
either. Its promise never rejects, since one invalid configuration
shouldn't stop you from choosing among the others.


## Clojure maps, not JavaScript objects

One rule converts the spec's names both ways, so a member that a
browser adds before play-nice knows about it passes through. A nil value
is left out on the way to JavaScript, since WebIDL turns a null string
into "null", while a missing member gets its default.


## What's left out

- **Audio Session.** Only Safari ships it, and Chrome has no plans. Safari
  needs it to show a Web Audio player on the lock screen, which
  [How to put a Web Audio player on the lock screen](web-audio.md) does
  by hand.
- **Player.js.** Its messages let a page drive a player in an iframe,
  which serves the sites that embed players more than the players.
- **Document Picture-in-Picture, Media Source Extensions and Encrypted
  Media Extensions.** They're about windows, streaming and DRM rather
  than a player's place in the system.
