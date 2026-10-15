# Notes from the inspector, October 2026

These are notes from moving the player of podcast-clj's inspector onto
play-nice 0.2.0: what was awkward, why, and what play-nice could change.
The inspector renders with Replicant and keeps its state in one atom, so
it's a fair test of an app that isn't re-frame.

## Open

### Waiting for a click after a refusal can loop

The inspector plays with `:muted-fallback`, and where the browser
refuses even that, it plays again with `on-activation!`. But
`on-activation!` calls its function at once while the page has
activation, so where `play!` is refused although the page is active,
e.g. by Safari's Never Auto-Play setting, play and wait call each other
without end. The inspector tries once more and then stops.

- **Library:** every app wants this pattern, so `play!` could take it as
  an option, e.g. `:retry-on-activation`, which tries once more at the
  next activation and never loops. What the promise answers then needs a
  decision: the first answer, with a way to get the second.

### Replicant's life-cycle hooks run inside the render

Loading the source and starting `watch!` in `:replicant/on-mount` calls
the watcher at once, and the swap into the app's atom renders again in
the middle of Replicant's render. Replicant queues that render and
complains in development. The inspector does its mount in a microtask.

- **Docs:** the guide to app state covers re-frame only. A short
  section on Replicant, with on-mount as data and the microtask, would
  have saved this.

### The seek bar speaks the browser's language, not the page's

`seek-aria` writes the position in words in the browser's language by
default. The inspector's page is in English, with `lang="en"`, and the
browser is Danish, so the seek bar's value text was "58 minutter og 4
sekunder", which a screen reader reads with the page's English voice.

- **Library:** default to the language of the page, i.e. the closest
  `lang` of the seek bar or the document's, and fall back to the
  browser's. The locale argument stays for an app that knows better.

### A volume slider needs the level of a state, not of an element

The bar renders from the state that `watch!` reports, as `seek-aria`
expects, but `level` takes the element. So the view looks up the curve in
`curves` and applies it to the state's `:volume` itself.

- **Library:** let `level` take the state, or a volume, as `seek-aria`
  takes the state.

### A seek bar needs the time under the pointer

`seek-aria` and `seek-bar-keys` cover the slider's attributes and keys,
but a click on the bar needs the time at the pointer, which the inspector
works out from `getBoundingClientRect`. Dragging needs pointer capture on
top. Every seek bar of its own needs this.

- **Library:** a function from a pointer event on the bar and the
  duration to the details of a seekto, e.g. `{:action :seekto :seek-time
  1830}`, and the tutorial on your own controls could use it for
  dragging too.

### `listen!` takes the handlers as they are at the call

The inspector's handlers close over the episode that plays, so it starts
`listen!` again at each episode. That's fine, but an app that keeps its
handlers in an atom has to know it.

- **Docs:** say in `listen!`'s docstring that it keeps the handlers it
  was given.

### Showing a track you added goes through languages

The inspector adds the transcript with `tracks/add!` and shows it, as
captions of a video, with `tracks/show!`. But `show!` chooses by kinds
and languages, so the inspector reads the language back from the
element's tracks and passes it in, and a transcript without a language
needs the feed's. The app knows which track it wants, since it added it.

- **Library:** let `show!` choose by label too, or let `add!` return the
  track's own way to show and hide it.

### The last chapter needs the duration before the metadata

`chapter-cues` ends the last chapter at the duration, and `add!` leaves
out a cue without a finite end. The chapters come from the server before
the element knows its duration, so the inspector uses the duration that
the feed declares, and without it the last chapter would be lost.

- **Library:** let the last cue end at the end of the media however long
  it turns out, e.g. by adding it again at `durationchange`, or say in
  `chapter-cues` how to wait for the duration.

### Feeds call Ogg Opus audio/opus, which no engine accepts

The item page asks the browser about each file of an episode. Buzzsprout
declares its Opus files as `audio/opus`, and Chrome 155, Firefox 157 and
WebKit 27.2 all answer no to that type, from `canPlayType` and from Media
Capabilities alike. The files are Ogg, and all three engines play
`audio/ogg; codecs="opus"`. So the page said "won't play" of a file that
plays everywhere.

- **podcast-clj:** `resolve/decoding-configuration` turns the feed's type
  into the content type, so it should write `audio/opus` as
  `audio/ogg; codecs="opus"`.
- **Library:** maybe `decoding-info!` should do the same for a type that
  names a codec rather than a container, since a page of its own meets
  the same type.

## Fixed

- `tracks/watch!` threw in jsdom, whose text tracks fire no events, and
  the inspector's jsdom test crashed. It now does nothing there, as the
  other functions do where an API is missing.
- The end of an episode isn't in the stream of states. In Chrome, the
  first state with `:ended` true has `:paused` true too, and a seek to
  the end while paused makes `:ended` true as well, so neither key says
  that the element played to its end. The `ended` event does. The
  docstring of `watch!`, the reference and the tutorial on your own
  controls now say so.

## What worked as designed

- `source!` takes what podcast-clj's `library/playback` returns. Before
  the metadata loads, the element answers `currentTime` with the position
  to start at, so the readout shows the resume position at once.
- The library's effects carry the episode id, e.g. `[:seek id 784]`, and
  play-nice's don't, so the inspector maps them for `apply!` in a few
  lines. That mapping belongs in podcast-clj, if anywhere.
- `tabs/attach!` was one line, and pausing one tab when another plays
  worked at once in Chrome.
- The muted fallback played in Chrome with its default autoplay policy,
  and unmuting from a click kept it playing.
- One map of handlers serves the lock screen, the keys and the bar's
  buttons. The Play button and the seek bar press it with the details of
  an action, and the library sees every press the same way.
- A key on the seek bar wins over the same key on the bar, since the
  seek bar's listener runs first and the bar's leaves alone a key that a
  handler took. Space doesn't scroll the page.
- `volume-locked!` answered false in Chrome at once, and the bar hides
  the slider until it has answered, so an iPhone never shows one.
- The `:chapter-info` of podcast-clj's `episode/metadata` went straight
  into `chapter-cues`, and a hidden chapters track named the chapter
  that plays as it changed.
- `fragment/link-time` read both `?t=492` and `?t=1m30s`, so a chapter's
  start on the item page links to the page at that time.
- `captions/style!` styled a video's captions at once, and choosing
  another style replaced the first.
- The browser pane refused the wake lock, and `hold-while-playing!` let
  the refusal pass without an error.
- Picture-in-Picture, fullscreen and casting answer in the same shape,
  with `:refused` where the browser says no, so one function in the
  inspector asks for any of the three and keeps a refusal for the bar.
  Picture-in-Picture and fullscreen worked in Chrome 155. Neither the
  browser pane nor Playwright's Chrome had a device to cast to, so
  `remote/watch!` said `:unavailable` and the bar left out its button.
