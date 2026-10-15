# Notes from the inspector, October 2026

These are notes from moving the player of podcast-clj's inspector onto
play-nice 0.2.0: what was awkward, why, and what play-nice could change.
The inspector renders with Replicant and keeps its state in one atom, so
it's a fair test of an app that isn't re-frame.

## Open

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
- Waiting for a click after a refusal could loop, since `on-activation!`
  calls its function at once while the page has activation, and a page
  may have it and still not play, e.g. by Safari's Never Auto-Play
  setting. `autoplay/play-when-allowed!` now plays muted where it may,
  and else once more at the next activation, and calls its function with
  each answer. It's a function of its own rather than an option of
  `play!`, since the answer comes twice and `media` can't require
  `page.autoplay`, which requires it.
- Replicant's life-cycle hooks run inside its render, so starting
  `watch!` at a mount renders again in the middle of it, which Replicant
  puts off and warns about. The Replicant section of the guide to app
  state now loads and follows the element with hooks as data, and mounts
  it in a microtask.
- The seek bar spoke the browser's language, not the page's: on the
  inspector's English page in a Danish browser, its value text was "58
  minutter og 4 sekunder", which a screen reader reads with the page's
  English voice. `value-text`, and so `seek-aria`, now default to the
  `lang` of the page's `html` element, and then to the browser's. It's
  the page's rather than the seek bar's closest `lang`, since
  `seek-aria` gets the element's state, not the bar.
- A volume slider needed the level of a state, but `level` took only
  the element, so the inspector's view applied the curve from `curves`
  itself. `level` now takes the state too, as `seek-aria` does.
- A seek bar needed the time under the pointer, which the inspector
  worked out from `getBoundingClientRect`. `controls/pointer-seek` now
  turns a pointer event on the bar and the element's state into the
  details of a seekto, which press the lock screen's handler, and the
  tutorial on your own controls drags with it under pointer capture.

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
