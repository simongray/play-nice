(ns dk.simongray.play-nice.media.capabilities
  "What the browser can play, from Media Capabilities and canPlayType.

  A configuration is a map of the keys of MediaDecodingConfiguration, e.g.

      {:type :file :audio {:content-type \"audio/mpeg\" :bitrate 128000}}

  On its own, Media Capabilities refuses some of the commonest files:

  - WebKit says it can't play MP3, and Firefox rejects the configuration.
  - Every engine refuses audio/mp4 without codecs, which is how most
    podcast feeds give AAC.
  - Every engine rejects a video without its width, height, bitrate and
    frame rate.

  So where it says no or rejects a configuration, the answer also has
  canPlayType's, and playable? accepts either.

  Servers and feeds also name some formats in ways that no engine knows,
  e.g. audio/opus for an Ogg Opus file, so decoding-info! asks about the
  name that the engines know of those in type-aliases."
  (:require [clojure.string :as str]
            [dk.simongray.play-nice.util.keys :as keys]))

;; RFC 7587 registers audio/opus for RTP, while an .opus file is Ogg, by
;; RFC 7845; Chrome 155, Firefox 157 and WebKit 27.2 refuse each name below
(def type-aliases
  "The media types that engines don't know, by the type of the same format
  that they do, which decoding-info! asks about in their place."
  {"audio/opus"  "audio/ogg; codecs=\"opus\""
   "audio/m4a"   "audio/mp4"
   "audio/mpeg3" "audio/mpeg"
   "audio/x-mp3" "audio/mpeg"})

(defn- known
  "The `configuration` with the content types that type-aliases name in
  the engines' own names."
  [configuration]
  (let [rename (fn [content-type]
                 (get type-aliases
                      (str/lower-case (str/trim (str content-type)))
                      content-type))]
    (cond-> configuration
      (get-in configuration [:audio :content-type])
      (update-in [:audio :content-type] rename)

      (get-in configuration [:video :content-type])
      (update-in [:video :content-type] rename))))

(defn- content-types
  "The content types of the video and audio in the `configuration`."
  [{:keys [video audio] :as configuration}]
  (keep :content-type [video audio]))

;; HTML, §4.8.11.3: canPlayType returns "", "maybe" or "probably"
(defn- weakest
  "The weakest of the canPlayType `answers`, or nil if there are none. Nil
  is weaker than :maybe, and :maybe than :probably."
  [answers]
  (when (and (seq answers) (every? some? answers))
    (if (some #{:maybe} answers) :maybe :probably)))

(defn can-play-type
  "The answer of canPlayType for the `content-type`: :probably, :maybe, or
  nil for no. Outside a browser, it's nil too."
  [content-type]
  (when (exists? js/document)
    (let [^js element (.createElement js/document "video")]
      (some-> (.canPlayType element content-type) not-empty keyword))))

;; Media Capabilities, Working Draft of 9 June 2026, §2.2: the members of
;; MediaCapabilitiesInfo, leaving out keySystemAccess, which is for EME
(defn decoding-info!
  "A promise of the browser's answer to whether it can play the
  `configuration`. The promise never rejects.

  The answer has :supported, :smooth and :power-efficient from Media
  Capabilities. Unless it supports the configuration, the answer also has
  :can-play-type, from canPlayType, e.g.

      {:supported false :smooth false :power-efficient false
       :can-play-type :maybe}

  For a configuration of both audio and video, :can-play-type is the
  weaker of the two answers. A content type of type-aliases is asked
  about by the engines' own name, e.g. audio/opus as Ogg Opus."
  [configuration]
  (let [configuration   (known configuration)
        ^js navigator   (.-navigator js/globalThis)
        ^js capabilities (some-> navigator .-mediaCapabilities)
        asked           (if capabilities
                          (.decodingInfo capabilities (keys/->js configuration))
                          (js/Promise.resolve nil))
        answer          (fn [info]
                          (cond-> info
                            (not (:supported info))
                            (assoc :can-play-type
                                   (->> (content-types configuration)
                                        (map can-play-type)
                                        weakest))))]
    (-> asked
        (.then (comp #(select-keys % [:supported :smooth :power-efficient])
                     keys/->clj))
        (.catch (constantly {}))
        (.then answer))))

(defn decoding-infos!
  "A promise of a map from each of the `configurations` to its answer from
  decoding-info!."
  [configurations]
  (let [configurations (distinct configurations)]
    (-> (js/Promise.all (into-array (map decoding-info! configurations)))
        (.then (partial zipmap configurations)))))

(defn playable?
  "Whether `info`, an answer from decoding-info!, says the browser can play
  the configuration, i.e. Media Capabilities supports it, or canPlayType
  says :probably or :maybe."
  [{:keys [supported can-play-type] :as info}]
  (boolean (or supported can-play-type)))
