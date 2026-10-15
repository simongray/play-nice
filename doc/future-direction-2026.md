# Anchor play-nice in HTML's media element

The reference that play-nice should adopt next isn't another device API but **the media element section of the HTML Living Standard (§4.8.11)**. Its `play()` promise, state model, load algorithm and rate rules are what every podcast app and every page with a `<video>` implements anyway. They work in every engine and hold the sharpest edges, including **a gap in play-nice 0.1.0's `session/attach!`**, which, by the spec and a Chromium probe of the element, keeps declaring "playing" after a fatal network or decode error. Behind HTML come the W3C pieces that sit on the element: text tracks with WebVTT cues, then Picture-in-Picture and Remote Playback, which the design already plans. **WCAG 2.2, EN 301 549 clause 7 and the European Accessibility Act** carry legal force in Denmark and the EU, so they work best as the acceptance test that sets play-nice's defaults (rate range, caption toggles, keyboard scope) rather than as code. Outside the web, Android's Media3, MPRIS and Apple's guidelines settle what the Media Session leaves open, e.g. what "previous" does and which skip seconds win. The rest ranks lower because one engine ships it, because it's page furniture rather than player plumbing, or because podcast-clj or a sibling already does it. Most of the new functions fit one proposed `media` namespace that follows the conventions 0.1.0 already has: plain maps, refusals as data, promises that never reject and watchers that return a stop function.

## Twenty-three references, ranked in one table

The ratings run from 1 (little use) to 5 (shapes the design), for **apps** (podcast-clj, music, audiobook or course players) and for **embeds** (ordinary websites that embed audio or video). "Kind" says plainly whether a reference is a standard, a W3C Note, a platform's own document or a convention, and "Adds" is how much a wrapper adds over calling the JavaScript yourself. Every function outside `session` and `capabilities`, and every new name inside them, is a proposal, not 0.1.0 API.

| # | Reference | Kind | Apps | Embeds | Support, Oct 2026 | Adds | Verdict |
|---|---|---|---|---|---|---|---|
| 1 | HTML `play()` and autoplay rules, Autoplay Policy Detection | Standard (WHATWG LS; W3C WD) | 5 | 5 | All engines; `getAutoplayPolicy` in Firefox only | High | Adopt now |
| 2 | HTML media element states, events, errors and TimeRanges | Standard (WHATWG LS) | 5 | 4 | All engines; the media pseudo-classes not in Chrome | High | Adopt now, `attach!` fix in 0.1.x |
| 3 | HTML load algorithm: sources, start position, resets | Standard (WHATWG LS) | 5 | 3 | All engines | High | Adopt now |
| 4 | Playback rate and pitch (HTML, UAAG 2.0 §2.10.4) | Standard + W3C Note | 5 | 3 | All engines, each with its own range | Medium | Adopt now |
| 5 | Text tracks and WebVTT cues | Standard (WHATWG LS; W3C CRD) | 4 | 4 | All engines | High | Next batch |
| 6 | WCAG 2.2, EN 301 549 clause 7, EAA and LOV 801 (with UAAG, MAUR, FCC) | Law, standards, W3C Notes | 4 | 5 | n/a | n/a | Reference only, sets defaults |
| 7 | Keyboard and slider semantics (WCAG 2.1.4 and 4.1.2, ARIA 1.2, APG, UI Events) | Standard + informative | 4 | 4 | All engines | Medium | Next batch |
| 8 | Page visibility, back/forward cache, Page Lifecycle | Standard (HTML) + CG draft | 4 | 3 | All engines; freeze events in Chromium only | Medium | Next batch |
| 9 | Picture-in-Picture and the session's `enterpictureinpicture` | W3C WD | 3 | 4 | Chrome, Firefox 153 desktop, Safari | Medium | Next batch |
| 10 | Remote Playback and WebKit's AirPlay API | W3C CRD + vendor API | 4 | 3 | Chrome and Safari; not Firefox | High | Next batch |
| 11 | Transport rules: Media3, MPRIS 2.2, Apple HIG, Android audio focus | Platform specs and docs | 4 | 3 | n/a (defaults) | Low | Next batch, small |
| 12 | Fullscreen, iPhone video fullscreen, Screen Orientation | Standard (WHATWG LS; W3C WD) | 2 | 5 | All engines, but iPhone only for `<video>` | High | Next batch |
| 13 | Caption styling (EN 301 549 7.1.4, 47 CFR 79.103(c), WebVTT `::cue`) | Law + standard | 3 | 3 | `::cue` in all engines | Medium | Next batch, with 5 |
| 14 | Viewport autoplay (IntersectionObserver, HTML "eligible for autoplay") | Standard + convention | 2 | 5 | All engines | Medium | Later |
| 15 | One player across tabs (Web Locks, BroadcastChannel) | W3C WD + Standard (HTML) | 4 | 3 | All engines | Medium | Later |
| 16 | Media Fragments parsing, page seek links, clip position | W3C Rec 2012 + convention | 3 | 3 | `#t=` in NPT form in all engines | Medium | Later |
| 17 | Screen Wake Lock | W3C WD | 3 | 2 | All engines | Medium | Later |
| 18 | Volume lock and volume curves | Standard (HTML) + convention | 3 | 3 | Volume locked on iPhone | Low | Later |
| 19 | Embedding: Permissions Policy and Player.js | W3C WD + convention | 2 | 4 | Safari ignores most `allow` tokens | Medium | Later |
| 20 | Audio Session | W3C WD | 3 | 2 | Safari's `type` only | Low | Later |
| 21 | Offline media: Storage, File System, Service Workers, HTTP ranges, Background Fetch | Standards + CG draft | 5 | 1 | All engines; Background Fetch in Chromium only | High | Later, likely a sibling library |
| 22 | Page metadata: schema.org, Google's video rules, Open Graph, oEmbed | Vocabularies + conventions | 2 | 3 | Crawlers, not engines | Medium | Later, as a `.cljc` sibling |
| 23 | Loudness: ITU-R BS.1770-5, EBU R 128, AES TD1008, platform targets | Standards + conventions | 3 | 2 | Pages can only attenuate | Low | Reference only |

## The ranked entries

### 1. `play()` rejects three ways, and only one of them is a real failure

**Apps 5, embeds 5.** **Kind:** Standard, the HTML Living Standard §4.8.11.8 and §6.4 (user activation), plus Autoplay Policy Detection, a W3C Working Draft of 4 September 2025. **Support:** Promises from `play()` since Chrome 50, Firefox 53 and Safari 10; `navigator.userActivation` is in Chrome 72, Firefox 120 and Safari 16.4; `getAutoplayPolicy` is in Firefox 112 only. **Adds:** A lot. **Verdict:** Adopt now. **Home:** In play-nice (`media`, `autoplay`).

HTML defines exactly three rejections: **`NotAllowedError` when the page isn't allowed to play, `NotSupportedError` when the source is unusable, and `AbortError` when a `pause()`, a new load or the end of the media came first** ([HTML §4.8.11.8](https://html.spec.whatwg.org/multipage/media.html#dom-media-play)). Only the first two are failures. Chrome's own article on the "play() request was interrupted" message tells developers never to assume a media element will play ([Chrome blog](https://developer.chrome.com/blog/play-request-was-interrupted)), yet most code still treats `AbortError` as an error or leaves the rejection unhandled. **The guide in podcast-clj does exactly that: its `play-episode!` ends in a bare `(.play audio)`.** In 0.1.0, play-nice already swallows the rejection inside `element-actions`; the proposal is to return it as data everywhere. The allowed-to-play rule itself is up to each engine: Chromium allows audible playback once a frame has sticky activation, a high Media Engagement score or an installed-app scope ([autoplay_policy.cc](https://github.com/chromium/chromium/blob/main/third_party/blink/renderer/core/html/media/autoplay_policy.cc)). HTML expects transient activation to last "at most a few seconds" ([HTML §6.4](https://html.spec.whatwg.org/multipage/interaction.html#tracking-user-activation)), and all three engines use 5 ([user_activation_state.h](https://github.com/chromium/chromium/blob/main/third_party/blink/public/common/frame/user_activation_state.h)). None of Media Chrome, Vidstack, Shaka, Plyr or Video.js v10 reads `navigator.userActivation` or `getAutoplayPolicy`, so this ground is uncovered.

The two notes rated this differently: the device note gave apps a 4, because it weighed the Firefox-only policy detection, while the media element note gave a 5 for `play!` itself. The merged rating is **5 for apps**, since every app calls `play()` from buttons, the lock screen and the queue. A `media/play!` that resolves to a map also suits core.async, whose `<p!` throws on a rejection and whose `p->c` closes the channel on nil ([core.async interop](https://github.com/clojure/core.async/blob/master/src/main/clojure/cljs/core/async/interop.cljs)).

```clojure
;; Proposed: media/play!, autoplay/policy and autoplay/on-activation!
(require '[dk.simongray.play-nice.media :as media]
         '[dk.simongray.play-nice.autoplay :as autoplay])

(defn press-play!
  "Play `audio`, and keep the browser's answer in `app`."
  [app audio]
  (-> (media/play! audio)
      (.then #(swap! app assoc :play %))))
;; :play is {:playing true}, {:refused :not-allowed},
;; {:refused :not-supported} or {:superseded true}

;; a hero video on a page, which may play only muted
(-> (media/play! hero {:muted-fallback true})
    (.then prn))
;; => {:playing true :muted true} where sound was refused

(autoplay/policy hero)
;; => :allowed-muted in Firefox, nil where the browser can't tell

(def stop!
  (autoplay/on-activation! (fn [_] (media/play! audio))))
```

### 2. A fatal error leaves `paused` false, so the lock screen keeps saying "playing"

**Apps 5, embeds 4.** **Kind:** Standard, HTML §4.8.11.1 to §4.8.11.16 and the media pseudo-classes of §4.16.3. **Support:** All engines; `:playing`, `:buffering`, `:stalled` and the other media pseudo-classes are in Safari 15.4 and Firefox 150 but not in Chrome ([BCD 8.1.5](https://github.com/mdn/browser-compat-data)). **Adds:** A lot. **Verdict:** Adopt now, and ship the `attach!` fix in a 0.1.x release. **Home:** In play-nice (`media`, and `session` for the fix).

HTML already defines a precise player state, but as numbers, live-looking `TimeRanges` objects and about twenty events whose order hides traps. The worst trap is now confirmed from the spec and a probe. **After a fatal network or decode error mid-playback, the element sets `error`, sets `networkState` to idle and fires `error`, and nothing else: no `pause`, `paused` stays false, and no promise rejects** ([HTML §4.8.11.5, media data processing steps](https://html.spec.whatwg.org/multipage/media.html#media-data-processing-steps-list); [fatal decode error](https://html.spec.whatwg.org/multipage/media.html#fatal-decode-error)). A headless Chromium 141 probe of a dropped HTTP connection and three Media Source cases showed only `error`, with `readyState` 4, `currentTime` frozen and a later `play()` resolving without sound. Gecko's source follows the same path, except that a decode error while playing from `<source>` children makes it try the next one ([HTMLMediaElement.cpp](https://searchfox.org/firefox-main/source/dom/media/mediaelement/HTMLMediaElement.cpp#6404)). **In play-nice, `attach!` follows no `error` event and `element-state` reads `paused` alone, so the declared playback state stays "playing".** The fix needs both halves: follow `error`, and let the state read it. Nobody has watched this on a real lock screen yet. A related silent case has no fix in `attach!`: when `<source>` children all fail, each fires `error` at its `<source>` and the media element waits forever with its play promise pending.

The state map brings three further gains. The attributes `buffered`, `seekable` and `played` return a fresh object on every read, which the spec itself calls "a bad pattern", so vectors of `[start end]` lose nothing ([HTML §4.8.11.5](https://html.spec.whatwg.org/multipage/media.html#dom-media-buffered)). The `played` ranges then give the seconds actually heard, and the `seekable` ones give a live stream's window, where the duration is infinite. And **HTML's own definition of buffering (not paused, network loading, `readyState` at most `HAVE_CURRENT_DATA`) gives Chrome users the `:buffering` status that its CSS lacks** ([HTML §4.16.3](https://html.spec.whatwg.org/multipage/semantics-other.html#selector-buffering)). The status keywords line up with Android Media3's idle, buffering, ready and ended states ([Media3 player events](https://developer.android.com/media/media3/exoplayer/listening-to-player-events)), which gives apps one vocabulary across web and native. The stall flag that `:stalled` uses isn't exposed by any attribute, so the watcher has to track it from `stalled` and `progress` events.

```clojure
;; Proposed: media/state and media/watch!
(media/state audio)
;; => {:status        :buffering
;;     :paused        false
;;     :ready-state   :have-current-data
;;     :network-state :loading
;;     :current-time  754.2
;;     :duration      3600
;;     :playback-rate 1.5
;;     :buffered      [[0 812.4]]
;;     :seekable      [[0 3600]]
;;     :played        [[0 754.2]]
;;     :error         nil}
;; after a fatal error, :status is :error and :error {:code :network …}

(def stop!
  (media/watch! audio #(swap! app assoc :player %)))

(media/watch! audio #(rf/dispatch [:player/changed %]))
```

The fix in 0.1.0's own code is small. Add `"error"` to `element-events`, add the element's error code (or nil) as `:error` to the private `element-attributes`, and change `element-state`:

```clojure
;; Proposed change to session/element-state
(defn element-state
  "The :playback-state and :position-state that the session shows for
  `element`, a map of a media element's attributes. The position state
  comes from `position-fn`, e.g. element-position.

  An element that failed counts as paused, since its paused attribute
  stays false after a fatal error."
  [position-fn {:keys [paused error] :as element}]
  {:playback-state (if (or paused error) :paused :playing)
   :position-state (position-fn element)})
```

### 3. A new source pauses silently and resets the rate

**Apps 5, embeds 3.** **Kind:** Standard, HTML §4.8.11.2, §4.8.11.5 and §4.8.11.6. **Support:** All engines. **Adds:** A lot. **Verdict:** Adopt now. **Home:** In play-nice (`media`).

Setting `src` runs the load algorithm, which **sets `paused` to true without a `pause` event, rejects pending play promises with `AbortError`, resets the duration to NaN without `durationchange`, and resets `playbackRate` to `defaultPlaybackRate`** ([HTML load algorithm](https://html.spec.whatwg.org/multipage/media.html#media-element-load-algorithm)). That last reset loses a podcast's per-show speed at every episode change. In return HTML offers a simpler resume: **`currentTime` set before the metadata loads becomes the default playback start position, which Chromium, WebKit and Gecko all honour** ([HTML §4.8.11.6](https://html.spec.whatwg.org/multipage/media.html#dom-media-currenttime); Gecko 160 at [HTMLMediaElement.cpp](https://searchfox.org/firefox-main/source/dom/media/mediaelement/HTMLMediaElement.cpp#6354)). **So podcast-clj's `loadedmetadata` listener is unnecessary, and it misfires when someone switches episode before the metadata arrives**, because the old one-shot event listener then seeks the new episode. A `source!` that sets things in the right order, and a `switch-source!` that keeps time, rate and play state for a quality or format change, cover what every player writes by hand. One more detail matters: in Chromium a `currentTime` set before load wins over a `#t=` fragment, so a caller should use one or the other.

The `preload` attribute is a hint that engines override (Chrome forces `metadata` on cellular, and iOS caps `auto` at `metadata`), so it belongs in the docs rather than in a function. The IAB's Podcast Measurement Guidelines v2.3 also ask players not to preload an episode without intent to play it, and not to add query parameters to the enclosure URL ([PTM v2.3 §7](https://github.com/IABTechLab/Podcast-Technical-Measurement/blob/main/PTM-Guidelines_v2.3.md)).

```clojure
;; Proposed: media/source! and media/switch-source!
;; in podcast-clj's play-episode!, instead of setting src and rate and
;; waiting for loadedmetadata
(media/source! audio {:src src :position position :rate rate})
(media/play! audio)

;; a quality menu on a page: the same time, rate and play state
(-> (media/switch-source! video "https://cdn.example/272-1080.mp4")
    (.then prn))
;; => {:playing true}, or {:paused true} if it was paused before
```

### 4. Each engine accepts a different playback rate, and Chromium throws

**Apps 5, embeds 3.** **Kind:** Standard (HTML §4.8.11.8) plus a W3C Note (UAAG 2.0 §2.10.4). **Support:** All engines, with the ranges below; `preservesPitch` in Chrome 86, Firefox 101 and Safari 17.2. **Adds:** A medium amount. **Verdict:** Adopt now. **Home:** In play-nice (`media`).

HTML lets each engine choose its range and throw `NotSupportedError` outside it ([HTML §4.8.11.8](https://html.spec.whatwg.org/multipage/media.html#dom-media-playbackrate)). **Chromium accepts 0 or 0.0625 to 16 and throws otherwise, and silently ignores a bad `defaultPlaybackRate`** ([html_media_element.cc](https://github.com/chromium/chromium/blob/main/third_party/blink/renderer/core/html/media/html_media_element.cc#L338)). **Gecko's current source (160) throws only for a negative rate, plays at 1/16 to 16, and mutes outside 1/8 to 8** ([HTMLMediaElement.cpp](https://searchfox.org/firefox-main/source/dom/media/mediaelement/HTMLMediaElement.cpp#7851); [the muting pref](https://searchfox.org/firefox-main/source/modules/libpref/init/StaticPrefList.yaml#14394)). WebKit never throws and alone plays backwards. The platform note quoted MDN's "Gecko mutes outside 0.25 to 4.0", but MDN's page was last changed in 2024 and Gecko's source is the primary source, so 1/8 to 8 stands.

UAAG 2.0 gives the only normative-sounding numbers: **a rate between 50% and 250%, pitch kept, audio and video in sync, and a reset to 100%** at level AA ([UAAG 2.0 §2.10.4](https://www.w3.org/TR/UAAG20/)). That's the right default range and the right preset ends, and it agrees with MPRIS's advice to offer "sensible fractions or multiples of 1" ([MPRIS Player](https://specifications.freedesktop.org/mpris-spec/latest/Player_Interface.html)). The media element note rated embeds 2; the merged 3 reflects that the WAI's media player guidance lists speed among a custom player's accessibility features ([WAI Media Players](https://www.w3.org/WAI/media/av/player/)). Setting `defaultPlaybackRate` along with `playbackRate` makes the speed survive entry 3's reset.

```clojure
;; Proposed: media/rate!
(media/rate! audio 1.75)
;; => {:playback-rate 1.75}

(media/rate! audio 20)
;; => {:playback-rate 16 :refused 20}

(media/rate! audio 2 {:preserves-pitch false})
;; => {:playback-rate 2 :preserves-pitch false}
```

### 5. Text tracks give every engine a current-chapter signal and captions without files

**Apps 4, embeds 4.** **Kind:** Standard, the HTML text track API (§4.8.11.11) and WebVTT, a W3C Candidate Recommendation Draft of 20 May 2026 ([WebVTT](https://www.w3.org/TR/2026/CRD-webvtt1-20260520/)). **Support:** The `TextTrack` API and `cuechange` in Chrome 23, Firefox 31 and Safari 6; the `VTTCue` constructor in Chrome 33, Firefox 31 and Safari 8. **Adds:** A lot. **Verdict:** Next batch. **Home:** In play-nice (`tracks`); writing WebVTT text stays in webvtt-clj and podcast-clj.

The Media Session's `chapterInfo` is Chromium-only and shown only on ChromeOS, while **a hidden `chapters` track turns `cuechange` into a "current chapter" event in every engine**. HTML says browsers never display a chapters track, so it exists for scripts ([HTML §4.8.11.11](https://html.spec.whatwg.org/multipage/media.html#attr-track-kind)), and the Chromium probe saw `cuechange` fire on a hidden track. The API has traps a wrapper handles once. **A track made with `addTextTrack` can never be removed and survives source changes** ([HTML forget tracks](https://html.spec.whatwg.org/multipage/media.html#forget-the-media-element's-media-resource-specific-tracks)), and a `<track>` without `src` fails in Chromium, while a `<track>` whose `src` is a Blob or `data:` URL of generated WebVTT loads and can be removed (Chromium probe only). Captions are legally required for video (entry 6), and EN 301 549 7.3 wants their toggle at the same level as the primary controls, so `tracks` should also find tracks, choose one by a cascade of kinds and languages, as MAUR's DAC-6 asks ([MAUR](https://www.w3.org/TR/media-accessibility-reqs/)), and show it. Driving custom-rendered captions from `cuechange` rather than `timeupdate`, which fires only every 15 to 250 ms, also helps meet EN 301 549's 100 ms sync limit.

The links note rated this 3 and 3, the media element note 4 and 4. The merged rating is 4 and 4, because captions on video carry legal force (WCAG 1.2.2, EN 301 549 7.1.1) and the chapter signal works where `chapterInfo` doesn't. Audio-description track switching stays limited, since `audioTracks` is in Safari only.

```clojure
;; Proposed: tracks/chapter-cues, tracks/add!, tracks/watch! and
;; tracks/show!. The chapters are play-nice's :chapter-info.
(tracks/chapter-cues [{:title "Intro" :start-time 0}
                      {:title "News" :start-time 95}]
                     1830)
;; => [{:start-time 0 :end-time 95 :text "Intro"}
;;     {:start-time 95 :end-time 1830 :text "News"}]

(def remove-chapters!
  (tracks/add! audio {:kind  :chapters
                      :label "Chapters"
                      :cues  (tracks/chapter-cues chapter-info duration)}))

(def stop!
  (tracks/watch! video #(swap! app assoc :tracks %)))
;; [{:kind :captions :language "da" :label "Dansk" :mode :showing
;;   :active-cues [{:start-time 12 :end-time 15.5 :text "…"}]} …]

(tracks/show! video {:kinds [:captions :subtitles] :languages ["da" "en"]})
```

### 6. Accessibility law binds video players, and its numbers become play-nice's defaults

**Apps 4 (5 for video apps, 2 for audio-only podcasts), embeds 5.** **Kind:** Law and harmonised standards (WCAG 2.2, EN 301 549, the EAA, Denmark's LOV 801, 47 CFR 79), plus W3C Notes (UAAG 2.0, MAUR) that aren't law. **Verdict:** Reference only. It has no functions of its own; it sets the defaults of entries 4, 5, 7 and 13. **Home:** In play-nice's defaults and docs.

**WCAG 2.2 is a W3C Recommendation and, since October 2025, ISO/IEC 40500:2025** ([W3C WAI](https://www.w3.org/WAI/news/2025-10-21/wcag22-iso/)). **EN 301 549 v3.2.1 is the version in force**, cited by Implementing Decision (EU) 2021/1339. **Version 4.1.1, published on 2 September 2026, isn't yet cited in the Official Journal**, so it gives no presumption of conformity; its "30 November 2026" is a deadline for national standards bodies, not a citation date ([EN 301 549 v4.1.1](https://www.etsi.org/deliver/etsi_en/301500_301599/301549/04.01.01_60/en_301549v040101p.pdf)). Clause 7 adds player duties beyond WCAG, but only "where ICT displays video with synchronized audio": show available captions, keep them within 100 ms, let users restyle them, play audio description, and put the caption and description controls "at the same level of interaction" as the primary controls ([EN 301 549 v3.2.1 §7](https://www.etsi.org/deliver/etsi_en/301500_301599/301549/03.02.01_60/en_301549v030201p.pdf)). **So clause 7 binds video, not audio-only podcasts**, which still owe a transcript under WCAG 1.2.1 ([WCAG 2.2 §1.2.1](https://www.w3.org/TR/WCAG22/#audio-only-and-video-only-prerecorded)). Version 4.1.1 renames captions "subtitles", calls 20 ms good practice, drops the duty to synthesise spoken subtitles, and asks for a single operation "at the same level as the volume control".

**The European Accessibility Act has applied since 28 June 2025**, and Annex I Section IV(b) requires services giving access to audiovisual media to transmit subtitles and audio description "synchronised with sound and video, while allowing for user control of their display and use" ([EAA Annex I §IV](https://www.legislation.gov.uk/eudr/2019/882/annex/I/section/IV)). **Denmark transposes it in LOV nr. 801 of 7 June 2022, and § 45(2) makes Sikkerhedsstyrelsen the supervisor for those audiovisual services** ([LOV 801](https://www.retsinformation.dk/eli/lta/2022/801)); Digitaliseringsstyrelsen supervises only the public-sector web law, LOV 692 of 2018. In the US, 47 CFR 79.103(c) gives the most detailed caption-style vocabulary, and its "readily accessible" rule applies to devices with next-generation operating systems deployed after 17 August 2026 ([eCFR §79.103](https://www.ecfr.gov/current/title-47/chapter-I/subchapter-C/part-79/subpart-B/section-79.103)). No legal analysis says whether a third-party player library is itself "ICT" or "apparatus"; the duties fall on the sites and services that use it, and play-nice can help them meet those duties.

The Notes supply the numbers the laws lack. **UAAG 2.0 numbers its time-based media guideline 2.10**: 2.10.4 sets the rate rules of entry 4, 2.10.6 asks for navigation by relative time, and 2.10.7 for navigation by chapters ([UAAG 2.0](https://www.w3.org/TR/UAAG20/)). MAUR adds the default cascade of alternatives and pausing for extended descriptions. The WCAG 3 draft of 10 September 2026 has a developing requirement that media of five minutes or more be navigable by chapters ([WCAG 3.0 WD](https://www.w3.org/TR/2026/WD-wcag-3.0-20260910/)), which a podcast app with chapters already meets.

### 7. WCAG 2.1.4 makes single-key shortcuts legal only while the player has focus

**Apps 4, embeds 4.** **Kind:** Standard (WCAG 2.2 §2.1.4 and §4.1.2, WAI-ARIA 1.2, a Recommendation of 6 June 2023) plus informative guidance (the APG) and the UI Events key values, a W3C Working Draft of 7 July 2023. **Support:** All engines; `Intl.DurationFormat` for localised value text in Chrome 129, Firefox 136 and Safari 16.4. **Adds:** A medium amount, as pure functions. **Verdict:** Next batch. **Home:** In play-nice (`controls`).

**A shortcut that uses a printable character must be one the user can turn off, remap, or use only while its component has focus** ([WCAG 2.2 §2.1.4](https://www.w3.org/TR/WCAG22/#character-key-shortcuts)). A focus scope by default meets that without a settings screen, and screen readers in browse mode swallow single keys anyway ([Understanding 2.1.4](https://www.w3.org/WAI/WCAG22/Understanding/character-key-shortcuts)). For a custom seek bar, ARIA requires `aria-valuenow` and a name ([WAI-ARIA 1.2 slider](https://www.w3.org/TR/wai-aria-1.2/#slider)), and **the APG's Media Seek Slider spells out value text such as "4 Minutes 3 Seconds", adding "of" the length only on load and on blur so that screen readers aren't verbose, and moving 15 steps on Page Up and Page Down** ([APG Media Seek Slider](https://www.w3.org/WAI/ARIA/apg/patterns/slider/examples/slider-seek/)). Native controls already meet WCAG 4.1.2, so these helpers serve only custom controls. The key names, e.g. `MediaPlayPause` and `MediaTrackNext`, come from UI Events ([UI Events key values](https://w3c.github.io/uievents-key/)) and match the Consumer Page usages of the HID Usage Tables 1.7 ([HUT 1.7](https://usb.org/sites/default/files/hut1_7.pdf)). Chromium routes the four hardware media keys to the active Media Session ([media_keys_listener.cc](https://github.com/chromium/chromium/blob/main/ui/base/accelerators/media_keys_listener.cc)), so pages rarely see them; the platform note's 2 for keys reflects that, while WCAG lifts the merged rating to 4.

The design point is that **a key can call the same handler map that the lock screen uses**, with the same details, e.g. `{:action :seekbackward :seek-offset 10}`, which `element-actions`' handlers already understand.

```clojure
;; Proposed: controls/listen! and controls/seek-aria.
;; session/element-actions and session/actions! are 0.1.0.
(def handlers
  (session/element-actions audio {:seekbackward 10 :seekforward 30}))

(session/actions! handlers)

(def stop!
  (controls/listen! player audio handlers))
;; while the player has focus, Space calls :play or :pause, and j and l
;; call the skips with {:seek-offset 10}

;; a custom seek bar in Replicant
[:div (merge {:class "seek" :tabindex 0}
             (controls/seek-aria (media/state audio)))]
;; seek-aria returns {:role "slider" :aria-valuemin 0
;;                    :aria-valuemax 3600 :aria-valuenow 750
;;                    :aria-valuetext "12 Minutes 30 Seconds"}
```

### 8. The back/forward cache pauses media, and only `pageshow` says so

**Apps 4, embeds 3.** **Kind:** Standard (page visibility, `pageshow` and `NotRestoredReasons` in HTML) plus the Page Lifecycle Community Group draft of 9 June 2022, which only Chromium ships. **Support:** The `visibilitychange` event everywhere (on navigation in Safari since 14.1); `freeze`, `resume` and `wasDiscarded` in Chromium 68; `notRestoredReasons` in Chrome 125. **Adds:** A medium amount. **Verdict:** Next batch. **Home:** In play-nice (`lifecycle`); podcast-clj uses it to save positions.

**HTML stops playback while a document isn't fully active, so a page restored from the back/forward cache comes back paused, with a stale playback state on the lock screen** ([HTML media elements](https://html.spec.whatwg.org/multipage/media.html)). The current advice on web.dev is to refresh such state on `pageshow` with `persisted` ([web.dev bfcache](https://web.dev/articles/bfcache)). **A page that has made sound in the last 30 seconds escapes Chrome's intensive timer throttling** ([Chrome 88 throttling](https://developer.chrome.com/blog/timer-throttling-in-chrome-88)), and Page Lifecycle notes that tabs playing audio are generally not frozen ([Page Lifecycle §5.4.1](https://wicg.github.io/page-lifecycle/#frozenness-state)). HTML also lists why a page couldn't use the cache: a held Web Lock, a BroadcastChannel message or an open Picture-in-Picture window ([HTML NotRestoredReasons](https://html.spec.whatwg.org/multipage/nav-history-apis.html#notrestoredreasons)), which matters for entries 9 and 15. Whether `attach!` should re-declare its state on `pageshow` is an open question, since nobody has tested what survives the cache.

```clojure
;; Proposed: lifecycle/watch! and lifecycle/not-restored-reasons
(def stop!
  (lifecycle/watch!
   (fn [{:keys [state]}]
     (when (= :hidden state)
       (save-position! (.-currentTime audio))))))
;; f gets {:state :hidden}, {:state :active}, {:state :frozen} or
;; {:event :pageshow :persisted true} after a restore from the cache

(lifecycle/not-restored-reasons)
;; => {:blocked true :reasons #{:lock :broadcastchannel-message}}
;;    in Chromium, and nil elsewhere
```

### 9. Picture-in-Picture now works in all three desktop engines, with stricter rules

**Apps 3, embeds 4.** **Kind:** W3C Working Draft of 16 June 2026 ([Picture-in-Picture](https://www.w3.org/TR/2026/WD-picture-in-picture-20260616/)). **Support:** Chrome 69, Chrome Android 105, **Firefox 153 on desktop** (21 July 2026), Safari 13.1 and iOS; `enterpictureinpicture` in Chrome 120 and its reason in Chrome 142 only. **Adds:** A medium to high amount. **Verdict:** Next batch, as design-2026.md §7 already plans. **Home:** In play-nice (`picture-in-picture`).

**The draft that design-2026.md cites already consumes user activation, returns rejected promises instead of throwing, exits fullscreen on entry, and no longer has a Media Session section** ([§4.1](https://www.w3.org/TR/2026/WD-picture-in-picture-20260616/)). It also contradicts itself: §4.1 rejects a blocked policy with `NotAllowedError` while §5.2 says `SecurityError`, which is what Chromium throws. A wrapper earns its place by mapping both to one keyword, retrying once after `loadedmetadata` (as Media Chrome does), and joining enter, resize and leave into one watcher. **Automatic PiP is narrower than the design says**: Chrome opens it through the `enterpictureinpicture` handler only on desktop, from Chrome 134 in a gradual rollout, for media in the top frame that was audible in the last two seconds and has audio focus, and only past a Media Engagement threshold unless the user allowed the site ([Chrome blog](https://developer.chrome.com/blog/automatic-picture-in-picture-media-playback)). Mozilla calls the session action "a backdoor for nonstandard features" ([mediasession#376](https://github.com/w3c/mediasession/issues/376)), and Apple asked for a second implementer on the spec PR ([picture-in-picture#241](https://github.com/w3c/picture-in-picture/pull/241)). The Media WG discussed it on 8 September 2026 without a resolution. Audio elements can't use video PiP at all.

```clojure
;; Proposed: picture-in-picture/enter! and picture-in-picture/watch!
(require '[dk.simongray.play-nice.picture-in-picture :as pip])

;; in a click handler
(-> (pip/enter! video)
    (.then #(swap! app assoc :pip %)))
;; {:width 320 :height 180}, or {:refused :not-allowed} without a click

(def stop!
  (pip/watch! video #(swap! app assoc :pip %)))
;; {:picture-in-picture true :width 320 :height 180} at each enter,
;; resize and leave

;; Chrome's button, and its automatic PiP when the tab is left
(session/actions!
 (assoc (session/element-actions video {:seekbackward 10 :seekforward 10})
        :enterpictureinpicture (fn [_] (pip/enter! video))))
```

### 10. Remote Playback needs WebKit's older AirPlay events to be reliable

**Apps 4, embeds 3.** **Kind:** W3C Candidate Recommendation Draft of 30 April 2024 ([Remote Playback](https://www.w3.org/TR/2024/CRD-remote-playback-20240430/)), plus WebKit's AirPlay API. **Support:** Chrome 121 on desktop, Chrome Android 56, Safari 13.1 and iOS; not Firefox or the Android WebView. **Adds:** A lot. **Verdict:** Next batch. **Home:** In play-nice (`remote-playback`), since podcast-clj leaves casting to apps.

The spec's error names are unusual: **`prompt()` without a gesture rejects with `InvalidAccessError`, a dismissed picker with `NotAllowedError`, and `watchAvailability` rejects with `NotSupportedError` when a browser can look for devices only while the picker is open** ([§5.2.1, §5.2.2](https://w3c.github.io/remote-playback/#prompt-user-for-changing-remote-playback-state)). On WebKit, Video.js v10 drives AirPlay state from `webkitplaybacktargetavailabilitychanged` and `webkitcurrentplaybacktargetiswirelesschanged` instead, saying Safari's `media.remote` events "don't fire reliably" ([remote-playback.ts](https://github.com/videojs/video.js/blob/main/packages/core/src/dom/store/features/remote-playback.ts)); that's Video.js's own claim, with no WebKit bug behind it. WebKit's picker also returns silently without a gesture. One more trap: on iOS, `x-webkit-airplay="allow"` overrides `disableremoteplayback` for video ([MediaElementSession.cpp](https://github.com/WebKit/WebKit/blob/main/Source/WebCore/html/MediaElementSession.cpp)), so a `disable!` must remove it.

```clojure
;; Proposed: remote-playback/watch! and remote-playback/prompt!
(require '[dk.simongray.play-nice.remote-playback :as remote])

(def stop!
  (remote/watch! audio #(swap! app assoc :remote %)))
;; {:availability :available :state :disconnected}; :availability is
;; :unknown where the browser looks for devices only in its picker

;; in the click handler of a cast button
(-> (remote/prompt! audio)
    (.then #(swap! app assoc :remote-prompt %)))
;; {:state :connecting}, or {:refused :not-allowed} when dismissed
```

### 11. Media3, MPRIS and Apple settle what the Media Session leaves open

**Apps 4, embeds 3.** **Kind:** Platform specifications and guidelines, not web standards: Android's Media3 `Player` (release 1.11.1) and audio focus guide, the freedesktop MPRIS 2.2 spec, Apple's Human Interface Guidelines and `MPRemoteCommandCenter`, with Windows SMTC, Bluetooth AVRCP 1.6.3 and the HID tables as vocabulary only. **Adds:** A little, as options and pure functions. **Verdict:** Next batch for two small changes to `element-actions`; reference only for the rest. **Home:** In play-nice (`session`); listening progress stays in podcast-clj.

The Media Session says `previoustrack` may restart the item or go to the previous one, and that a missing `seekOffset` means "a sensible time" ([Media Session](https://w3c.github.io/mediasession/)). **Media3 settles both: previous restarts the item unless the position is under 3 seconds and a previous item exists** ([Media3 Player](https://developer.android.com/reference/androidx/media3/common/Player); [C.java](https://github.com/androidx/media/blob/release/libraries/common/src/main/java/androidx/media3/common/C.java)). The skip seconds are less reliable than they look: **Chromium passes an MPRIS Seek to the page's handler without its offset** ([media_session_impl.cc](https://github.com/chromium/chromium/blob/main/content/browser/media/session/media_session_impl.cc)), and **WebKit hard-codes 15 seconds as the skip interval it offers** ([RemoteCommandListenerCocoa.mm](https://github.com/WebKit/WebKit/blob/main/Source/WebCore/platform/cocoa/RemoteCommandListenerCocoa.mm)). According to WebKit's source, then, a handler that trusts the delivered offset, as `seek-position` does, skips 15 seconds on iOS even where the app's preference is 30; nobody has watched this on a device. An option to prefer the app's seconds fixes it. Pocket Casts and AntennaPod send the headphones' next and previous to skips by default, Apple offers it as an option, and the HIG warns against repurposing controls ([HIG Playing audio](https://developer.apple.com/design/human-interface-guidelines/playing-audio)), so skips-as-track-buttons should be opt-in.

Android explains a class of out-of-step bugs: **Chrome for Android applies headphone unplugs and audio focus losses to the players directly, bypassing the page's pause and play handlers** ([media_session_impl.cc](https://github.com/chromium/chromium/blob/main/content/browser/media/session/media_session_impl.cc); [MediaSessionHelper.java](https://github.com/chromium/chromium/blob/main/components/browser_ui/media/android/java/src/org/chromium/components/browser_ui/media/MediaSessionHelper.java)). That's why app state should follow the element (entry 2) rather than the handlers. The repeat vocabulary agrees everywhere (off, one, all), but the Media Session has no repeat, shuffle or rate action, and the requests for them have had no progress ([mediasession#267](https://github.com/w3c/mediasession/issues/267), [#361](https://github.com/w3c/mediasession/issues/361)), so play-nice shouldn't model them. The merged rating drops Media3's 5 to 4 because the resume rewinds, the 30-second played threshold and the chapter jump with its 3-second restart already live in podcast-clj.

```clojure
;; Proposed: a third argument to session/element-actions, and
;; session/previous
(session/element-actions audio
                         {:seekbackward 15 :seekforward 30}
                         {:offsets       :ours
                          :track-buttons :skips})
;; skips 15 and 30 s even where WebKit asks for 15, and makes the
;; headphones' next and previous skip too

(session/previous {:position 2.1 :previous? true})  ; => :previous
(session/previous {:position 42 :previous? true})   ; => :restart
```

### 12. On an iPhone, only the video element itself can go fullscreen

**Apps 2, embeds 5.** **Kind:** Standard, the WHATWG Fullscreen Living Standard (review draft July 2026) and Screen Orientation, a W3C Working Draft of 6 August 2026. **Support:** The `requestFullscreen` method in Chrome 71, Firefox 64 and Safari 16.4, but **on iOS only on iPad**; `screen.orientation.lock()` on Chrome Android and Firefox 144 (Android and Windows tablets), not in Safari. **Adds:** A lot on iPhone. **Verdict:** Next batch, for embedding sites. **Home:** In play-nice (`fullscreen`).

Every custom video player needs fullscreen, and **the iPhone is the edge everyone hits: element fullscreen isn't there, so a player falls back to the video's own `webkitEnterFullscreen` and its `webkitbeginfullscreen` and `webkitendfullscreen` events** ([BCD Element.json](https://github.com/mdn/browser-compat-data/blob/main/api/Element.json); [HTMLVideoElement.idl](https://github.com/WebKit/WebKit/blob/main/Source/WebCore/html/HTMLVideoElement.idl)). The standard's `requestFullscreen` consumes user activation and rejects with `TypeError` ([Fullscreen](https://fullscreen.spec.whatwg.org/#dom-element-requestfullscreen)), and Screen Orientation now requires fullscreen before `lock()` ([Screen Orientation](https://www.w3.org/TR/2026/WD-screen-orientation-20260806/)), so a refused lock should be data, not noise. Safari also ignores `allow="fullscreen"` and needs `allowfullscreen` on an iframe. This is more visible than play-nice's "invisible" scope, which is why it ranks below the plumbing despite the embeds' 5.

```clojure
;; Proposed: fullscreen/enter! and fullscreen/watch!
;; in a click handler
(-> (fullscreen/enter! player {:video       video
                               :orientation :landscape})
    (.then #(swap! page assoc :fullscreen %)))
;; {:fullscreen :element}, or {:fullscreen :video} on an iPhone, where
;; only the video element itself can go fullscreen

(def stop!
  (fullscreen/watch! player #(swap! page assoc :fullscreen %)
                     {:video video}))
```

### 13. One caption-style vocabulary serves the FCC, Apple, Android and `::cue`

**Apps 3 (4 for video apps), embeds 3.** **Kind:** Law (EN 301 549 7.1.4, 47 CFR 79.103(c)) and standard (WebVTT's `::cue`). **Support:** The `::cue` pseudo-element in Chrome 26, Firefox 55 and Safari 7; `::cue-region` support is unknown. **Adds:** A medium amount, as a pure function. **Verdict:** Next batch, together with entry 5. **Home:** In play-nice (`captions`).

EN 301 549 7.1.4 asks video players to let users adapt the captions, and 47 CFR 79.103(c) names the vocabulary: **sizes from 50% to 200%, a palette of at least eight colours, three opacities, five edge styles and the eight CEA-708 font styles** ([eCFR §79.103](https://www.ecfr.gov/current/title-47/chapter-I/subchapter-C/part-79/subpart-B/section-79.103)). Apple's MediaAccessibility and Android's CaptioningManager use the same set. WebVTT lets `::cue` take colour, opacity, background, outline, text shadow and font properties and nothing else, with defaults of `5vh sans-serif` in white on 80% black ([WebVTT ::cue](https://www.w3.org/TR/webvtt1/#the-cue-pseudo-element)), which covers everything but the window colour and the position. **A page can't read the operating system's caption settings**, since the browser vendors who have spoken treat them as a fingerprinting risk, and Safari's `showCaptionDisplaySettings()` remains internal and off by default on WebKit's main branch. So the app stores the user's style and play-nice turns it into CSS.

```clojure
;; Proposed: captions/css and captions/style!
(captions/css {:font-scale 1.5 :color "#ffff00" :edge :drop-shadow})
;; => "video::cue{font:calc(5vh * 1.5) sans-serif;
;;     color:rgba(255,255,0,1);background-color:rgba(0,0,0,0.8);
;;     text-shadow:2px 2px 3px rgba(0,0,0,1)}"

(def remove-style!
  (captions/style! video (:caption-style @prefs)))
```

### 14. Engines pause muted autoplay videos off screen, but never those started with `play()`

**Apps 2, embeds 5.** **Kind:** Standard (IntersectionObserver, a W3C Working Draft; HTML's "eligible for autoplay"), with the behaviour itself a convention that mirrors engine policy. **Support:** IntersectionObserver in Chrome 51, Firefox 55 and Safari 12.1. **Adds:** A medium amount. **Verdict:** Later. **Home:** In play-nice (`viewport`).

**Blink at Chrome 155, on desktop and Android, pauses a muted `<video autoplay>` when it scrolls out of view and plays it again when it returns; WebKit does the same on iOS; neither touches media started with `play()`** ([autoplay_policy.cc at 155](https://raw.githubusercontent.com/chromium/chromium/155.0.8059.81/third_party/blink/renderer/core/html/media/autoplay_policy.cc); [WebKit iOS video policies](https://webkit.org/blog/6784/new-video-policies-for-ios/)). A library rule that plays and pauses with `play!` gives the same behaviour everywhere. It has to remember a visitor's own pause so it doesn't override it, and it should hold off under reduced motion.

```clojure
;; Proposed: viewport/autoplay!
(def stop!
  (viewport/autoplay! hero #(swap! page assoc :hero %) {:threshold 0.5}))
;; plays the video muted while half of it shows, pauses it when it
;; scrolls away, and leaves it alone once the visitor pauses it
```

### 15. On desktop, no engine stops one tab's audio when another starts

**Apps 4, embeds 3.** **Kind:** Web Locks, a W3C Working Draft of 24 September 2025, and BroadcastChannel in HTML. **Support:** Web Locks in Chrome 69, Firefox 96 and Safari 15.4. **Adds:** A medium amount. **Verdict:** Later. **Home:** In play-nice (`tabs`); podcast-clj keeps its sync locks.

**Chrome puts all tabs in one audio-focus group so they play at once** ([controlling_media_playback.md](https://chromium.googlesource.com/chromium/src/+/HEAD/services/media_session/controlling_media_playback.md)), and Firefox's audio focus management is on only on Android, so two tabs of a podcast app double-play and race to save positions. A lock taken with `steal: true` ([Web Locks](https://www.w3.org/TR/web-locks/)) tells the previous holder to pause. It must be released on pause and `pagehide`, or it keeps the page out of the back/forward cache.

```clojure
;; Proposed: tabs/attach!
(def detach!
  (tabs/attach! audio "my-podcast-app"))
;; holds a Web Lock while audio plays, and pauses it when another tab of
;; the app takes the lock
```

### 16. `#t=` is the only standard timestamp, and it addresses the file, not the page

**Apps 3, embeds 3.** **Kind:** W3C Recommendation, "Media Fragments URI 1.0 (basic)" of 25 September 2012 ([Media Fragments](https://www.w3.org/TR/media-frags/)), plus the page-level `?t=` convention. **Support:** All engines parse `#t=` in its NPT form, seek to the start and pause once at the end. **Adds:** A medium amount, as pure functions. **Verdict:** Later. **Home:** In play-nice (`fragment`, and `session/clip-position`).

The links note rated this 4 and 4 with writing included, but **podcast-clj's `links/at-time` already writes media fragments**, so play-nice should only parse them and offer what's new: a `clip-position` that makes the lock screen show a soundbite's 60 seconds instead of the whole hour, and parsing page seek links. Google requires a `?t=` page URL for a video's key moments ([Google video structured data](https://developers.google.com/search/docs/appearance/structured-data/video)), and `?t=` must stay off the enclosure URL (entry 3). Spotify's and YouTube's own `t` forms remain undocumented (still unverified).

```clojure
;; Proposed: fragment/time and session/clip-position
(fragment/time "https://cdn.example/272.mp3#t=npt:73,133")
;; => {:start 73 :end 133}

(session/attach! audio {:position-fn (partial session/clip-position
                                              {:start 73 :end 133})})
;; the lock screen shows a 60-second clip, not the whole episode
```

### 17 to 23. The lower ranks: useful, but narrow, single-engine or off the element

**17. Screen Wake Lock** (apps 3, embeds 2; W3C Working Draft of 29 September 2026; Chrome 84, Firefox 126, Safari 16.4; later). **No engine keeps the display on for audio-only playback** ([WebKit shouldDisableSleep](https://github.com/WebKit/WebKit/blob/main/Source/WebCore/html/HTMLMediaElement.cpp)), so a transcript or lyrics view needs a lock, re-requested whenever the page becomes visible again ([Screen Wake Lock](https://www.w3.org/TR/2026/WD-screen-wake-lock-20260929/)). A proposed `(wake-lock/hold-while-playing! audio)` would return a stop function.

**18. Volume lock and curves** (apps 3, embeds 3; HTML's "volume locked" plus convention; later). **WebKit locks `volume` on iPhone but not on iPad**, against BCD's note ([HTML §4.8.11.13](https://html.spec.whatwg.org/multipage/media.html#volume-locked); [WebKit bug 193745](https://bugs.webkit.org/show_bug.cgi?id=193745)), so a slider is dead on iPhone. HTML says only that the range "need not be linear"; cubic curves are convention (PulseAudio uses one). A proposed `(media/volume! audio 0.5 {:curve :cubic})` would return `{:volume 0.125}` or `{:refused :volume-locked}`.

**19. Embedding: Permissions Policy and Player.js** (apps 2, embeds 4; W3C Working Draft of 22 September 2026 plus a dormant 2017 convention; later). Chrome 155 ships `media-playback-while-not-visible`, which lets a parent pause audible media in hidden iframes ([Chrome 155 beta](https://developer.chrome.com/blog/chrome-155-beta)), and Safari honours almost none of the media `allow` tokens. Player.js lives on as Video.js 10's `<playerjs-video>` ([Video.js docs](https://videojs.org/docs/framework/html/reference/components/playerjs-video)). A pure `(embed/allow {:autoplay true :fullscreen true})` and a Player.js client and receiver would fit, but they serve hosts of players more than players.

**20. Audio Session** (apps 3, embeds 2; W3C Working Draft of 13 November 2024; Safari's `type` since 16.4, Firefox Nightly only, no Chrome plans; later). **Safari shows a Web Audio player on the lock screen only when the page sets the type to "playback"**, which Apple calls an iOS platform requirement ([mediasession#378](https://github.com/w3c/mediasession/issues/378)). A one-line `(audio-session/type! :playback)` should come when a Web Audio user needs it, as design-2026.md §7 says.

**21. Offline media** (apps 5, embeds 1; the WHATWG Storage and File System standards, Service Workers, RFC 9110 ranges, and Background Fetch, a dormant CG draft in Chromium only; later, likely a sibling library). Serving a cached episode needs a full 200 sliced into 206 answers and CORS mode ([Workbox](https://developer.chrome.com/docs/workbox/serving-cached-audio-and-video)), and Safari deletes script-written storage after seven days of use without interaction, except for a Home Screen app's own domain ([WebKit tracking prevention](https://webkit.org/tracking-prevention/)). The storage-agnostic plumbing (persist, estimate, save with progress, object URLs) is generic, while the proxy and the choice of episodes stay in podcast-clj. It's big enough to be a library of its own rather than part of play-nice.

**22. Page metadata** (apps 2, embeds 3; schema.org 30.1, Google's video documentation, Open Graph and oEmbed 1.0; later, as a `.cljc` sibling). **Link-preview crawlers don't run JavaScript**, so JSON-LD, Open Graph tags and oEmbed responses must come from the server ([Mastodon link_details_extractor.rb](https://github.com/mastodon/mastodon/blob/main/app/lib/link_details_extractor.rb)). Their names (`@type`, `og:image:width`, `author_name`) also break play-nice's kebab-case rule. Google has no podcast feature in search, and the schema.org podcast types are still pending ([schema.org PodcastEpisode](https://schema.org/PodcastEpisode)). One input map extending the Media Session metadata could drive all three, from a library that runs on the JVM too.

**23. Loudness** (apps 3, embeds 2; reference only). ITU-R BS.1770-5 (November 2023) defines the measurement, EBU R 128 targets −23 LUFS, and Apple asks podcasts for about −16 LKFS ([Apple audio requirements](https://podcasters.apple.com/support/893-audio-requirements)); AES TD1008's −18 LUFS for speech is still unverified because aes.org was unreachable. A page can't do much with them: `volume` can only attenuate and is locked on iPhone, and boosting through Web Audio silences cross-origin media without CORS ([Web Audio §1.22.4](https://webaudio.github.io/web-audio-api/#MediaElementAudioSourceOptions-security)). Spotify's web player doesn't normalise at all ([Spotify](https://support.spotify.com/us/artists/article/loudness-normalization/)).

## One shape for every namespace: `watch!`, maps and promises that never reject

The proposals add one namespace per concern beside the three that exist: `media` (state, `watch!`, `play!`, `source!`, `rate!`, `apply!`) as the hub, then `autoplay`, `tracks`, `captions`, `controls`, `lifecycle`, `picture-in-picture`, `remote-playback` and `fullscreen`, with `viewport`, `tabs`, `fragment`, `wake-lock`, `embed` and `audio-session` later. **Once `media/watch!` exists, `session/attach!` is that watcher plus `element-state`**, so the Media Session and the app's own state follow the same events, including `error`. Every namespace keeps 0.1.0's rules: a `support` that returns nil where the API is missing, functions that do nothing there, results as maps, refusals as `{:refused :not-allowed}` with the exception's name as a keyword, and an options map last whose stand-in key (`:session` today, `:navigator` or `:document` elsewhere) lets tests run in Node. Promises never reject and never resolve to nil, since core.async closes a channel on nil.

The proposed `watch!` takes `[target f opts]`, calls `f` once at the start and then only when the map changes, and returns a stop function. That's the shape missionary's `observe`, React's `useSyncExternalStore`, Helix, UIx and Replicant's life-cycle memory all consume ([missionary](https://github.com/leonoel/missionary/blob/master/src/missionary/core.cljc); [React](https://react.dev/reference/react/useSyncExternalStore); [Replicant](https://replicant.fun/life-cycle-hooks/)), so play-nice needs no dependency on any of them, and React's need for an identical snapshot is met by de-duplicating in the library. It leaves out `timeupdate` unless asked, as `attach!` does, because a re-frame player like Tubo shows the cost of putting time updates in app-db ([Tubo](https://github.com/migalmoreno/tubo/blob/421ced4ffff1443d38093598919ae14e329f1746/src/frontend/tubo/player/subs.cljs)). For effects, a dependency-free `media/apply!` takes vectors such as `[:seek 120]` that fit both re-frame's `:fx` tuples and Nexus actions ([Nexus](https://github.com/cjohansen/nexus/blob/56906a0c7ce46d9acee209dd000cc6434c19c82c/Readme.md)), and returns what it couldn't apply. The `apply-effects!` in podcast-clj's guide could then map its own effects onto them.

```clojure
;; Proposed: media/watch! and media/apply!
(media/watch! audio #(reset! player %))                  ; an atom
(media/watch! audio #(rf/dispatch [:player/changed %]))  ; re-frame

(let [ch (a/chan (a/sliding-buffer 1))]                  ; core.async
  (media/watch! audio #(a/put! ch %)))

(m/relieve (m/observe (partial media/watch! audio)))     ; missionary

;; Replicant keeps the stop function with the node
[:audio {:replicant/on-mount
         (fn [{:replicant/keys [node remember]}]
           (remember (media/watch! node #(reset! player %))))
         :replicant/on-unmount
         (fn [{:replicant/keys [memory]}]
           (memory))}]

;; effects as data, registered by the app, not by play-nice
(rf/reg-fx :media #(media/apply! audio %))
(nxr/register-effect! :media
                      (fn [_ _ & effects] (media/apply! audio effects)))
```

The missionary adapter needs `m/relieve`, because `observe`'s callback throws in ClojureScript when the consumer falls behind. The library shouldn't ship re-frame, Nexus, core.async or missionary namespaces, since each adapter is a line in the app and each namespace would add a dependency that the build doesn't declare. Two smaller choices follow podcast-clj: publish the maps' shapes as Malli vector data in an optional `schema` namespace without depending on Malli, and write the pure parts (status, rules, value text, fragment parsing) as `.cljc`, so that podcast-clj's `.cljc` code and tests on the JVM can use them.

## What to leave out, and why

| Reference | Why it stays out | Where it belongs |
|---|---|---|
| Document Picture-in-Picture | Still a WICG Community Group draft, in Chrome 116 and Firefox 151 on desktop only, absent from Safari and every mobile browser, and unavailable to iframes. The reasons in design-2026.md hold, and WebKit's standards position (#41) is open with none recorded. | Neither, for now |
| Media Source Extensions, ManagedMediaSource, EME | Byte pipelines that hls.js, Shaka and dash.js already handle; play-nice follows the element they drive. | Players |
| Presentation API | Chrome only, and it sends whole pages, not media. | Neither |
| Audio Output Devices (`setSinkId`, `selectAudioOutput`) | Desktop only, the picker in Firefox only, and output ids gated on microphone permission. Safari has it on iOS since 26, against BCD. | Neither |
| Gapless playback and crossfading | HTML's request for it ended WONTFIX; it needs MSE or Web Audio. Docs can show two elements with `:position-fn`. | Players |
| `requestVideoFrameCallback`, `getVideoPlaybackQuality`, `captureStream`, `audioTracks`, `controlsList` | Niche or single-engine. | Neither |
| IAB VAST, VMAP, SIMID, OM SDK and Podcast Measurement | Ad SDK territory. Keep `:skipad`; put PTM §7's player recommendations in the docs. | Ad SDKs |
| Subresource Integrity for media | No browser applies `integrity` to media, and podcast-clj's `feed/integrity` already checks files. | podcast-clj |
| Writing WebVTT text | Both webvtt-clj and podcast-clj's `transcript/emit-vtt` write it. | webvtt-clj |
| Writing media fragment links | The `links/at-time` function in podcast-clj writes them. | podcast-clj |
| Resume rewinds, the played threshold, sleep timers | The preferences and `library/step` of podcast-clj already model them. | podcast-clj |
| Network Information and Save-Data | Chromium only, Mozilla is negative, and Chrome sends `Save-Data` only under Android's data saver on metered networks. | Neither |
| Web Share, manifest `share_target` and `launch_handler`, Badging | Not media-specific; the manifest is static JSON, and podcast-clj already documents badging. | Apps |
| Trim silence and voice boost | They need Web Audio and an AudioWorklet, and Web Audio outputs silence for cross-origin media served without CORS headers. How many podcast hosts lack them is still unverified. | Neither |
| Repeat, shuffle, rate and like in the session's maps | No Media Session action exists for them, and the requests (#267, #361) have no progress. | App state |
| MPRIS, SMTC and AVRCP as code | Browsers expose only the basic player core; they inform entry 11 only. | Reference |
| re-frame, Nexus, core.async and missionary namespaces | Each adapter is one line, and a namespace would add an undeclared dependency. | Apps |

## Five corrections to design-2026.md stand, and two proposed ones don't

| design-2026.md | Correction | Source |
|---|---|---|
| §1.5 calls Firefox 157 current, as does reference.md | **Firefox 158** shipped on 13 October 2026, and 159 is due on 23 October. | [Mozilla product details](https://product-details.mozilla.org/1.0/firefox_versions.json) |
| §1.3 and §3.6 cite the Picture-in-Picture WD of 16 June 2026 without its changes | That draft consumes user activation, returns rejected promises instead of throwing, exits fullscreen on entry and has no Media Session section. Its §4.1 says `NotAllowedError` for a policy block while §5.2 says `SecurityError`, which Chromium throws. | [WD §4.1, §5.2](https://www.w3.org/TR/2026/WD-picture-in-picture-20260616/) |
| §3.6: a handler that calls `enter!` "gives automatic Picture-in-Picture in Chrome" | Only on desktop, rolled out gradually from Chrome 134, for top-frame media audible in the last two seconds with audio focus, and past an engagement threshold unless the user allowed the site. The mechanism is disputed in mediasession#376 and picture-in-picture#241. | [Chrome blog](https://developer.chrome.com/blog/automatic-picture-in-picture-media-playback); [Media WG, 8 Sep 2026](https://www.w3.org/2026/09/08-mediawg-minutes.html) |
| §1.6 names Media Chrome as the model for the later batch | **Video.js v10** (10.1.0, 12 October 2026) now gathers the Media Chrome, Vidstack and Plyr teams, and on WebKit it drives AirPlay from WebKit's own events instead of `media.remote`. | [Vidstack README](https://github.com/vidstack/player/blob/main/README.md); [remote-playback.ts](https://github.com/videojs/video.js/blob/main/packages/core/src/dom/store/features/remote-playback.ts) |
| §2 "Media element events" lists `emptied` without `durationchange` | A new source also sets `paused` without a `pause` event and resets the rate; a fatal mid-stream error fires only `error` with `paused` false; failing `<source>` children fire nothing at the element. The second is the `attach!` gap of entry 2. | [HTML §4.8.11.5](https://html.spec.whatwg.org/multipage/media.html#media-data-processing-steps-list) |

Two further updates are small. Firefox 155 lists Audio Session as an experimental pref, and Nightly gained a pref that blocks playback during interruptions. Apple replied on mediasession#378 that Safari's "playback" requirement comes from iOS. The guide in podcast-clj, quoted in §5.2 of the design, should handle the `play()` promise and set `currentTime` right after `src`.

Two corrections that the notes first proposed were **refuted** in verification. **Document Picture-in-Picture:** The design's §1.5 already lists Chrome 116 and Firefox 151, and its reasons for leaving it out still hold; at most it could add Mozilla's positive position and WebKit's open issue #41. **Safari 27's `setActionHandler` fix:** It doesn't make `session/support` over-report. The fix adds hangup, previousslide, nextslide and enterpictureinpicture behind `MediaSessionExtendedActionsEnabled`, a testable flag that's off by default, and with it off `setActionHandler` still throws a `TypeError` ([WebKit 7503ebe](https://github.com/WebKit/WebKit/commit/7503ebe002c0a59ad64c4844e0458d363cbf6df9)). Nobody has checked this in a Safari 27 binary.

**Open questions** remain where only a device, another engine or an unreachable host can answer:

| Question | Why it's open |
|---|---|
| What a real lock screen shows after the `attach!` gap | Confirmed from the spec and a probe, not seen on a device |
| What survives the back/forward cache: the declared state, metadata and handlers | Untested in any engine |
| Whether real Chrome refuses unmuted autoplay and video PiP without activation | Headless Chromium 141 allowed both, which the docs say real Chrome doesn't (still unverified) |
| Firefox's and WebKit's event orders, and their PiP activation rules | No Firefox or WebKit runs |
| Whether a focused page receives `keydown` for media keys while a Media Session is active | No primary source; needs a test in each browser |
| `::cue-region` support, and whether native WebVTT rendering applies the OS's caption styles | No BCD entry |
| Casting Blob or MSE sources, seeking in Blob URLs, and whether `persist()` escapes Safari's 7-day eviction | Each needs a device or browser test (still unverified) |
| When EN 301 549 v4.1.1 is cited in the Official Journal, and whether a player library is "ICT" | No citing decision as of 15 October 2026; no legal analysis found |
| AES TD1008, TD1004 and ReplayGain 2.0 figures; Spotify's and YouTube's `t` forms; whether X still renders player cards | aes.org and hydrogenaudio behind bot challenges; undocumented by the vendors (still unverified) |
| Whether Danish universities fall under LOV 692 | Not named in the Act (still unverified) |

**Method and limits.** The notes were written in two passes. The first ran under a network policy that blocked w3.org, whatwg.org, MDN, ETSI, EUR-Lex, eCFR and others, so it leaned on source repositories and search summaries. The second, a verification pass on 15 October 2026, checked every marked claim against its primary source and edited the notes in place, and this report uses the verified or corrected version wherever the two differ. **All probes ran in headless Chromium 141 only, with no Firefox, WebKit or device runs**, and two of its results (autoplay and PiP without activation) differ from what Chrome documents. **EUR-Lex, iso.org, fcc.gov, aes.org and podcastindex.org stayed unreachable or behind bot challenges**; EU law was read from the UK National Archives' copy of the Official Journal, the Podcast Namespace from its GitHub repository, and FCC rules from eCFR and docs.fcc.gov. The ratings are judgements from the cited evidence, not usage measurements.

## Conclusion

The research moves play-nice's centre of gravity. The design treated the Media Session as the core and the element as something `attach!` follows, but the element's own algorithms turn out to be where apps and embeds lose most time, and where the gap in `attach!` sits. A `media` namespace that turns HTML's states, rejections and resets into data would make `session` a thin consumer of it, and would give podcast-clj the two fixes its guide needs without a dependency between the libraries.

The second shift is in how to use references that aren't web APIs. Accessibility law, Media3, MPRIS and Apple's guidelines can't be wrapped, but they answer the questions the Media Session leaves open, and they do so with numbers: 50% to 250%, 3 seconds, 100 ms, focus-scoped keys. Encoded as defaults and options, those numbers would make the accessible, conventional choice the default for any player built on play-nice, which is a stronger claim to being the go-to Clojure media library than wrapping more APIs. The parts that live on the page rather than the player (offline storage, crawler metadata) fit better in `.cljc` siblings than in play-nice.

## Sources

**WHATWG standards:** [HTML, media elements §4.8.11](https://html.spec.whatwg.org/multipage/media.html) (notably [play()](https://html.spec.whatwg.org/multipage/media.html#dom-media-play), [the load algorithm](https://html.spec.whatwg.org/multipage/media.html#media-element-load-algorithm), [the media data processing steps](https://html.spec.whatwg.org/multipage/media.html#media-data-processing-steps-list), [playbackRate](https://html.spec.whatwg.org/multipage/media.html#dom-media-playbackrate), [volume locked](https://html.spec.whatwg.org/multipage/media.html#volume-locked)); [HTML §4.16.3 media pseudo-classes](https://html.spec.whatwg.org/multipage/semantics-other.html#selector-buffering); [HTML §6.4 user activation](https://html.spec.whatwg.org/multipage/interaction.html#tracking-user-activation); [HTML NotRestoredReasons](https://html.spec.whatwg.org/multipage/nav-history-apis.html#notrestoredreasons); [Fullscreen](https://fullscreen.spec.whatwg.org/).

**W3C specifications:** [Media Session WD, 5 June 2026](https://www.w3.org/TR/2026/WD-mediasession-20260605/); [Picture-in-Picture WD, 16 June 2026](https://www.w3.org/TR/2026/WD-picture-in-picture-20260616/); [Remote Playback CRD, 30 April 2024](https://www.w3.org/TR/2024/CRD-remote-playback-20240430/); [Autoplay Policy Detection WD](https://www.w3.org/TR/2025/WD-autoplay-detection-20250904/); [Audio Session WD](https://www.w3.org/TR/2024/WD-audio-session-20241113/); [WebVTT CRD, 20 May 2026](https://www.w3.org/TR/2026/CRD-webvtt1-20260520/); [Screen Wake Lock WD](https://www.w3.org/TR/2026/WD-screen-wake-lock-20260929/); [Screen Orientation WD](https://www.w3.org/TR/2026/WD-screen-orientation-20260806/); [Web Locks WD](https://www.w3.org/TR/web-locks/); [Permissions Policy WD](https://www.w3.org/TR/permissions-policy-1/); [Media Fragments URI 1.0 (basic)](https://www.w3.org/TR/media-frags/); [Web Audio API](https://webaudio.github.io/web-audio-api/); [UI Events key values](https://w3c.github.io/uievents-key/); [WAI-ARIA 1.2](https://www.w3.org/TR/wai-aria-1.2/); [APG Media Seek Slider](https://www.w3.org/WAI/ARIA/apg/patterns/slider/examples/slider-seek/); [Page Lifecycle (WICG)](https://wicg.github.io/page-lifecycle/).

**Accessibility and law:** [WCAG 2.2](https://www.w3.org/TR/WCAG22/); [WCAG 2.2 as ISO/IEC 40500:2025](https://www.w3.org/WAI/news/2025-10-21/wcag22-iso/); [WCAG 3.0 WD, 10 September 2026](https://www.w3.org/TR/2026/WD-wcag-3.0-20260910/); [UAAG 2.0](https://www.w3.org/TR/UAAG20/); [MAUR](https://www.w3.org/TR/media-accessibility-reqs/); [WAI Media Players](https://www.w3.org/WAI/media/av/player/); [EN 301 549 v3.2.1](https://www.etsi.org/deliver/etsi_en/301500_301599/301549/03.02.01_60/en_301549v030201p.pdf); [EN 301 549 v4.1.1](https://www.etsi.org/deliver/etsi_en/301500_301599/301549/04.01.01_60/en_301549v040101p.pdf); [EAA Annex I §IV](https://www.legislation.gov.uk/eudr/2019/882/annex/I/section/IV); [LOV nr. 801 of 2022](https://www.retsinformation.dk/eli/lta/2022/801); [LOV nr. 692 of 2018](https://www.retsinformation.dk/eli/lta/2018/692); [47 CFR 79.103](https://www.ecfr.gov/current/title-47/chapter-I/subchapter-C/part-79/subpart-B/section-79.103); [Section 508 ICT standards](https://www.access-board.gov/ict/).

**Platforms outside the web:** [Media3 Player](https://developer.android.com/reference/androidx/media3/common/Player); [Media3 C.java](https://github.com/androidx/media/blob/release/libraries/common/src/main/java/androidx/media3/common/C.java); [Android audio focus](https://developer.android.com/media/optimize/audio-focus); [MPRIS 2.2](https://specifications.freedesktop.org/mpris-spec/latest/Player_Interface.html); [Apple HIG, Playing audio](https://developer.apple.com/design/human-interface-guidelines/playing-audio); [MPRemoteCommandCenter](https://developer.apple.com/documentation/mediaplayer/mpremotecommandcenter); [HID Usage Tables 1.7](https://usb.org/sites/default/files/hut1_7.pdf); [ITU-R BS.1770-5](https://www.itu.int/rec/R-REC-BS.1770); [EBU R 128](https://tech.ebu.ch/docs/r/r128.pdf); [EBU R 128 s2](https://tech.ebu.ch/docs/r/r128s2.pdf); [Apple Podcasts audio requirements](https://podcasters.apple.com/support/893-audio-requirements).

**Engine source and support data:** [MDN browser-compat-data](https://github.com/mdn/browser-compat-data); Chromium's [html_media_element.cc](https://github.com/chromium/chromium/blob/main/third_party/blink/renderer/core/html/media/html_media_element.cc), [media_session_impl.cc](https://github.com/chromium/chromium/blob/main/content/browser/media/session/media_session_impl.cc) and [autoplay_policy.cc](https://github.com/chromium/chromium/blob/main/third_party/blink/renderer/core/html/media/autoplay_policy.cc); Gecko's [HTMLMediaElement.cpp](https://searchfox.org/firefox-main/source/dom/media/mediaelement/HTMLMediaElement.cpp); WebKit's [HTMLMediaElement.cpp](https://github.com/WebKit/WebKit/blob/main/Source/WebCore/html/HTMLMediaElement.cpp), [MediaElementSession.cpp](https://github.com/WebKit/WebKit/blob/main/Source/WebCore/html/MediaElementSession.cpp) and [RemoteCommandListenerCocoa.mm](https://github.com/WebKit/WebKit/blob/main/Source/WebCore/platform/cocoa/RemoteCommandListenerCocoa.mm); [Chrome's automatic PiP](https://developer.chrome.com/blog/automatic-picture-in-picture-media-playback); [Chrome's play() article](https://developer.chrome.com/blog/play-request-was-interrupted).

**Prior art and Clojure idioms:** [Video.js v10 remote-playback.ts](https://github.com/videojs/video.js/blob/main/packages/core/src/dom/store/features/remote-playback.ts); [Media Chrome state-mediator.ts](https://github.com/muxinc/media-chrome/blob/main/src/js/media-store/state-mediator.ts); [missionary](https://github.com/leonoel/missionary/blob/master/src/missionary/core.cljc); [React useSyncExternalStore](https://react.dev/reference/react/useSyncExternalStore); [Replicant life-cycle hooks](https://replicant.fun/life-cycle-hooks/); [Nexus](https://github.com/cjohansen/nexus/blob/56906a0c7ce46d9acee209dd000cc6434c19c82c/Readme.md); [core.async interop](https://github.com/clojure/core.async/blob/master/src/main/clojure/cljs/core/async/interop.cljs); [Tubo](https://github.com/migalmoreno/tubo).
