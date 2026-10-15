(ns dk.simongray.play-nice.schema
  "The shapes of the library's maps as Malli schemas, written as plain data
  so that the library doesn't depend on Malli. With Malli in your app:

      (require '[malli.core :as m])

      (m/validate schema/media-state (media/state audio))
      ;; => true

  The maps are open, as Malli's are by default, so a key that a browser
  adds passes."
  (:require [dk.simongray.play-nice.captions :as captions]))

(def ranges
  "Time ranges, as from media/ranges: pairs of a start and an end in
  seconds."
  [:vector [:tuple 'number? 'number?]])

(def media-error
  "The error of a media element, as media/error gives it."
  [:map
   [:code [:enum :aborted :network :decode :src-not-supported]]
   [:message {:optional true} :string]])

(def media-state
  "The state of a media element, as media/state gives it and media/watch!
  passes on."
  [:map
   [:status [:enum :error :ended :buffering :idle :ready]]
   [:paused :boolean]
   [:ended :boolean]
   [:seeking :boolean]
   [:ready-state [:enum :have-nothing :have-metadata :have-current-data
                  :have-future-data :have-enough-data]]
   [:network-state [:enum :empty :idle :loading :no-source]]
   [:current-time 'number?]
   [:duration [:maybe 'number?]]
   [:playback-rate 'number?]
   [:muted :boolean]
   [:volume 'number?]
   [:buffered ranges]
   [:seekable ranges]
   [:played ranges]
   [:error [:maybe media-error]]])

(def play-answer
  "What the promise of media/play! resolves to."
  [:or
   [:map [:playing [:= true]] [:muted {:optional true} [:= true]]]
   [:map [:superseded [:= true]]]
   [:map [:refused :keyword]]])

(def source
  "The map that media/source! loads."
  [:map
   [:src :string]
   [:position {:optional true} 'number?]
   [:playback-rate {:optional true} 'number?]])

(def media-effect
  "An effect that media/apply! applies."
  [:or
   [:cat [:= :play] [:? [:map [:muted-fallback {:optional true} :boolean]]]]
   [:cat [:= :pause]]
   [:cat [:= :seek] 'number?]
   [:cat [:= :rate] 'number? [:? [:map [:preserves-pitch {:optional true} :boolean]]]]
   [:cat [:= :volume] 'number? [:? [:map [:curve {:optional true} [:enum :linear :cubic]]]]]
   [:cat [:= :muted] :boolean]
   [:cat [:= :source] source]
   [:cat [:= :switch-source] :string]])

(def image
  "An image of the Media Session's metadata, a MediaImage."
  [:map
   [:src :string]
   [:sizes {:optional true} :string]
   [:type {:optional true} :string]])

;; W3C Media Session, §6 to §8
(def metadata
  "The metadata that session/metadata! shows, a MediaMetadataInit."
  [:map
   [:title {:optional true} :string]
   [:artist {:optional true} :string]
   [:album {:optional true} :string]
   [:artwork {:optional true} [:sequential image]]
   [:chapter-info {:optional true}
    [:sequential [:map
                  [:title {:optional true} :string]
                  [:start-time {:optional true} 'number?]
                  [:artwork {:optional true} [:sequential image]]]]]])

;; §9, MediaPositionState
(def position-state
  "The position state that session/position! shows."
  [:map
   [:duration 'number?]
   [:playback-rate 'number?]
   [:position 'number?]])

;; §5 and §10, MediaSessionAction and MediaSessionActionDetails
(def action-details
  "The details that a handler of the Media Session gets."
  [:map
   [:action [:enum :play :pause :seekbackward :seekforward :previoustrack
             :nexttrack :skipad :stop :seekto :togglemicrophone :togglecamera
             :togglescreenshare :hangup :previousslide :nextslide
             :enterpictureinpicture :voiceactivity]]
   [:seek-offset {:optional true} 'number?]
   [:seek-time {:optional true} 'number?]
   [:fast-seek {:optional true} :boolean]])

(def caption-style
  "A style of captions, as captions/css takes it."
  [:map
   [:font-scale {:optional true} 'number?]
   [:font {:optional true} (into [:enum] (keys captions/fonts))]
   [:color {:optional true} [:or (into [:enum] (keys captions/colors)) :string]]
   [:opacity {:optional true} 'number?]
   [:background-color {:optional true} [:or (into [:enum] (keys captions/colors)) :string]]
   [:background-opacity {:optional true} 'number?]
   [:edge {:optional true} (into [:enum] (keys captions/edges))]
   [:edge-color {:optional true} [:or (into [:enum] (keys captions/colors)) :string]]])

(def interval
  "The interval of a media fragment, as fragment/time gives it."
  [:map
   [:start 'number?]
   [:end {:optional true} 'number?]])

(def fullscreen-state
  "The state that fullscreen/state gives and fullscreen/watch! passes on."
  [:map [:fullscreen [:enum :element :video false]]])

(def picture-in-picture-state
  "The state that picture-in-picture/watch! passes on."
  [:map
   [:picture-in-picture :boolean]
   [:width {:optional true} 'number?]
   [:height {:optional true} 'number?]])

(def remote-playback-state
  "The state that remote-playback/watch! passes on."
  [:map
   [:availability [:enum :available :unavailable :unknown]]
   [:state [:enum :disconnected :connecting :connected]]])
