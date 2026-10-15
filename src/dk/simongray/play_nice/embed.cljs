(ns dk.simongray.play-nice.embed
  "The allow attribute of an iframe that embeds a player from another site,
  built from a map of the Permissions Policy features that the player may
  use.

  Without the attribute, a player from another site can't go fullscreen,
  and its document reports document.fullscreenEnabled as false."
  (:require [clojure.string :as str]))

;; W3C Permissions Policy, §9.7 the allow attribute's serialized policy
(defn- allowlist
  "The allowlist of `who` after a feature, or nothing for the frame's own
  origin."
  [who]
  (cond
    (true? who)       ""
    (= "*" who)       " *"
    (keyword? who)    (str " '" (name who) "'")
    (sequential? who) (str " " (str/join " " who))
    :else             (str " " who)))

(defn allow
  "The allow attribute of an iframe for the `policy`, a map from each
  feature to who may use it in the frame, e.g.

      (allow {:autoplay true :fullscreen true :picture-in-picture true})
      ;; => \"autoplay; fullscreen; picture-in-picture\"

  The features of a player are :autoplay, :fullscreen, :picture-in-picture,
  :encrypted-media, :screen-wake-lock and Chrome's
  :media-playback-while-not-visible. Who may use one is:

  - true, the frame's own origin, as the attribute has it by default
  - \"*\", any origin the frame goes to
  - :none, no one, e.g. to pause a hidden frame's media with
    :media-playback-while-not-visible
  - a vector of origins, e.g. [\"https://player.example\"]

  False and nil leave the feature out."
  [policy]
  (->> (sort-by key policy)
       (keep (fn [[feature who]]
               (when who
                 (str (name feature) (allowlist who)))))
       (str/join "; ")))
