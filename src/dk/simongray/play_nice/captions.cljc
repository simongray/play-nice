(ns dk.simongray.play-nice.captions
  "Captions in the style that the user prefers, as the CSS of ::cue, from a
  map of the settings that the FCC's rule for caption displays names.

  A page can't read the system's own caption settings, so you keep the
  user's style and the functions turn it into CSS:

      (css {:font-scale 1.5 :color :yellow :edge :drop-shadow})
      ;; => \"video::cue{color:rgba(255,255,0,1);font-size:150%;
      ;;     text-shadow:2px 2px 3px rgba(0,0,0,1)}\"

  The style is a map of these keys, each optional, which leaves the
  browser's own setting:

  - :font-scale, the size as a share of the browser's own, from 0.5 to 2
    as the rule asks, e.g. 1.5
  - :font, one of fonts
  - :color and :background-color, one of colors, or a hex code, e.g.
    \"#ffcc00\"
  - :opacity and :background-opacity, from 0 to 1, e.g. 0.5 for
    semi-transparent
  - :edge, one of edges, and :edge-color, black by default

  Chrome 153 on macOS kept its own background behind the text, and its own
  letters for :small-capitals, whatever the page asked for, but applied the
  rest. Everything but style! runs on the JVM too, e.g. to write the CSS
  into a page on the server."
  (:require [clojure.string :as str]))

;; 47 CFR 79.103(c)(2) and (6): a palette of at least these eight colours
(def colors
  "The colours of the text and the background that a user may choose
  from, as RGB."
  {:white   [255 255 255]
   :black   [0 0 0]
   :red     [255 0 0]
   :green   [0 255 0]
   :blue    [0 0 255]
   :yellow  [255 255 0]
   :magenta [255 0 255]
   :cyan    [0 255 255]})

;; 47 CFR 79.102(k): the eight font styles of CEA-708, in typefaces that the
;; rule names as examples, with fallbacks
(def fonts
  "The font styles that a user may choose from, as CSS font families, of
  which :default keeps the browser's own."
  {:default                 nil
   :monospaced-serif        "\"Courier New\",Courier,monospace"
   :proportional-serif      "\"Times New Roman\",Times,serif"
   :monospaced-sans-serif   "\"Andale Mono\",Menlo,Consolas,monospace"
   :proportional-sans-serif "Arial,Helvetica,sans-serif"
   :casual                  "\"Comic Sans MS\",\"Comic Neue\",sans-serif"
   :cursive                 "\"Apple Chancery\",\"Monotype Corsiva\",cursive"
   :small-capitals          "Arial,Helvetica,sans-serif"})

;; 47 CFR 79.103(c)(7), drawn with text-shadow, the one property of ::cue
;; that every engine applies around the letters
(def edges
  "The edges of the letters that a user may choose from, as functions of
  the colour of the edge to a CSS text-shadow."
  {:none        (constantly "none")
   :raised      #(str/join "," (for [n [1 2 3]] (str n "px " n "px 0 " %)))
   :depressed   #(str "1px 1px 0 rgba(255,255,255,0.8),-1px -1px 0 " %)
   :uniform     #(str/join "," (repeat 4 (str "0 0 2px " %)))
   :drop-shadow #(str "2px 2px 3px " %)})

(defn- channel
  "The value of the two hex digits at `i` in `hex`, or NaN where there
  aren't two, as parseInt answers in JavaScript."
  [hex i]
  (let [s (subs hex (min i (count hex)) (min (+ i 2) (count hex)))]
    #?(:clj  (try (Integer/parseInt s 16)
                  (catch NumberFormatException _ ##NaN))
       :cljs (js/parseInt s 16))))

(defn rgba
  "The CSS colour of `color`, one of colors or a hex code, e.g. \"#ffcc00\"
  or \"#fc0\", at the `opacity`, from 0 to 1, e.g. \"rgba(255,204,0,1)\"."
  [color opacity]
  (let [hex (str/replace (str color) #"^#" "")
        hex (if (= 3 (count hex))
              (str/join (mapcat #(repeat 2 %) hex))
              hex)
        rgb (or (colors color)
                (mapv (partial channel hex) [0 2 4]))]
    (str "rgba(" (str/join "," rgb) "," opacity ")")))

(defn declarations
  "The CSS declarations of the caption `style`, a map of the keys above, as
  pairs of property and value, e.g. [[\"font-size\" \"150%\"]]."
  [{:keys [font-scale font color opacity background-color background-opacity
           edge edge-color]
    :as   style}]
  (cond-> []
    (or color opacity)
    (conj ["color" (rgba (or color :white) (or opacity 1))])

    font-scale
    (conj ["font-size" (str (Math/round (double (* 100 font-scale))) "%")])

    (fonts font)
    (conj ["font-family" (fonts font)])

    (= :small-capitals font)
    (conj ["font-variant" "small-caps"])

    edge
    (conj ["text-shadow" ((edges edge) (rgba (or edge-color :black) 1))])

    (or background-color background-opacity)
    (conj ["background-color" (rgba (or background-color :black)
                                    (or background-opacity 0.8))])))

;; WebVTT, §8.2.1, the ::cue pseudo-element, whose default is 5vh
;; sans-serif in white on rgba(0,0,0,0.8)
(defn css
  "The CSS of the caption `style` for the videos that the `selector`
  chooses, by default every video, e.g. \"video::cue{font-size:150%}\".

  The size is a percentage of the browser's own, since a page's vh is the
  page's viewport, not the video's, in every engine."
  ([style]
   (css style "video"))
  ([style selector]
   (str selector "::cue{"
        (str/join ";" (map (fn [[property value]] (str property ":" value))
                           (declarations style)))
        "}")))

#?(:cljs
   (defonce ^{:no-doc true
              :doc    "The number of the last video that style! styled."}
     styled
     (atom 0)))

#?(:cljs
   (defn style!
     "Style the captions of the `video` in the `style`, and return a function
     that removes the style again.

     The call adds the CSS of css to the video's document, for this video
     alone. A later call for the same video replaces its style, and the
     earlier call's function then only removes that style's element."
     [^js video style]
     (if-let [^js document (.-ownerDocument video)]
       (let [^js element (.createElement document "style")
             id          (str (swap! styled inc))]
         (.setAttribute video "data-play-nice-captions" id)
         (set! (.-textContent element)
               (css style (str "video[data-play-nice-captions=\"" id "\"]")))
         (.appendChild (or (.-head document) (.-documentElement document)) element)
         (fn []
           (.remove element)
           (when (= id (.getAttribute video "data-play-nice-captions"))
             (.removeAttribute video "data-play-nice-captions"))
           nil))
       (constantly nil))))
