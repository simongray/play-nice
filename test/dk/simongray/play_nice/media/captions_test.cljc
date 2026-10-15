(ns dk.simongray.play-nice.media.captions-test
  "The CSS of caption styles."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [dk.simongray.play-nice.media.captions :as captions]))

(deftest colours
  (is (= "rgba(255,255,0,1)" (captions/rgba :yellow 1)))
  (is (= "rgba(255,204,0,0.5)" (captions/rgba "#ffcc00" 0.5)))
  (is (= "rgba(255,204,0,1)" (captions/rgba "#fc0" 1)) "a short hex code")
  (is (= "rgba(171,NaN,NaN,1)" (captions/rgba "#ab" 1))
      "NaN for what isn't a hex code, as in JavaScript")
  (is (= 8 (count captions/colors)) "the palette of the FCC's rule")
  (is (= 8 (count captions/fonts)) "the font styles of CEA-708")
  (is (= #{:none :raised :depressed :uniform :drop-shadow}
         (set (keys captions/edges)))))

(deftest the-css
  (is (= (str "video::cue{color:rgba(255,255,0,1);font-size:150%;"
              "text-shadow:2px 2px 3px rgba(0,0,0,1)}")
         (captions/css {:font-scale 1.5 :color :yellow :edge :drop-shadow})))
  (is (= "video::cue{}" (captions/css {})) "the browser's own style")
  (is (= "#player::cue{font-size:110%}" (captions/css {:font-scale 1.1} "#player"))
      "a selector of your own, and a whole percentage")
  (is (= "video::cue{font-size:200%}" (captions/css {:font-scale 2}))
      "a whole scale")
  (is (= [["color" "rgba(255,255,255,0.5)"]]
         (captions/declarations {:opacity 0.5}))
      "semi-transparent text, white by default")
  (is (= [["background-color" "rgba(0,0,0,0)"]]
         (captions/declarations {:background-opacity 0}))
      "a transparent background, black by default")
  (is (= [["background-color" "rgba(0,0,255,0.8)"]]
         (captions/declarations {:background-color :blue}))
      "at the opacity of WebVTT's own")
  (is (= [["font-family" "Arial,Helvetica,sans-serif"]
          ["font-variant" "small-caps"]]
         (captions/declarations {:font :small-capitals})))
  (is (= [] (captions/declarations {:font :default})))
  (is (= [["text-shadow" (str/join "," (repeat 4 "0 0 2px rgba(255,0,0,1)"))]]
         (captions/declarations {:edge :uniform :edge-color :red}))))

#?(:cljs
   (deftest styling-without-a-document
     (is (nil? ((captions/style! #js {} {:color :yellow}))))))
