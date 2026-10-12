(ns dk.simongray.play-nice.capabilities
  "What the browser can play, from Media Capabilities and canPlayType.

  A configuration is a map of the keys of MediaDecodingConfiguration, e.g.

      {:type :file :audio {:content-type \"audio/mpeg\" :bitrate 128000}}

  Media Capabilities alone gets the files that apps meet wrong. It says no
  to a container type without a codecs parameter, e.g. audio/mp4, which is
  how most podcast feeds give AAC, and it rejects a video without its
  width, height, bitrate and frame rate. So where it says no or rejects,
  the answer also has what canPlayType says, and playable? takes either."
  (:require [dk.simongray.play-nice.keys :as keys]))

(defn- content-types
  "The content types of the video and the audio of the `configuration`."
  [{:keys [video audio] :as configuration}]
  (keep :content-type [video audio]))

;; HTML, §4.8.11.3: canPlayType answers "", "maybe" or "probably"
(defn- weakest
  "The weakest of the canPlayType `answers`, where nil is weaker than
  :maybe and :maybe than :probably, or nil without any."
  [answers]
  (when (and (seq answers) (every? some? answers))
    (if (some #{:maybe} answers) :maybe :probably)))

(defn can-play-type
  "What canPlayType says about the `content-type`: :probably, :maybe, or
  nil for no, and nil where the page has no document."
  [content-type]
  (when (exists? js/document)
    (let [^js element (.createElement js/document "video")]
      (some-> (.canPlayType element content-type) not-empty keyword))))

;; Media Capabilities, Working Draft of 9 June 2026, §2.2: the members of
;; MediaCapabilitiesInfo, without what's for EME
(defn decoding-info!
  "A promise of what the browser says about playing the `configuration`,
  which never rejects.

  The answer has the :supported, :smooth and :power-efficient of Media
  Capabilities where it answers. Where it doesn't support the
  configuration, the answer also has the :can-play-type of its content
  types, the weakest for both audio and video, e.g.

      {:supported false :smooth false :power-efficient false
       :can-play-type :maybe}"
  [configuration]
  (let [^js navigator   (.-navigator js/globalThis)
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
  "A promise of a map of each of the `configurations` to what
  decoding-info! says about it."
  [configurations]
  (let [configurations (distinct configurations)]
    (-> (js/Promise.all (into-array (map decoding-info! configurations)))
        (.then (partial zipmap configurations)))))

(defn playable?
  "Whether the answer `info` of decoding-info! says that the browser can
  play its configuration: Media Capabilities supports it, or canPlayType
  says :probably or :maybe."
  [{:keys [supported can-play-type] :as info}]
  (boolean (or supported can-play-type)))
