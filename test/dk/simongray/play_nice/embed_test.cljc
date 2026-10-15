(ns dk.simongray.play-nice.embed-test
  "The allow attribute of an iframe that embeds a player."
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.play-nice.embed :as embed]))

(deftest the-allow-attribute
  (is (= "autoplay; fullscreen; picture-in-picture"
         (embed/allow {:picture-in-picture true :fullscreen true :autoplay true}))
      "in a stable order")
  (is (= "autoplay *; encrypted-media https://a.example https://b.example"
         (embed/allow {:autoplay        "*"
                       :encrypted-media ["https://a.example" "https://b.example"]})))
  (is (= "media-playback-while-not-visible 'none'"
         (embed/allow {:media-playback-while-not-visible :none})))
  (is (= "fullscreen" (embed/allow {:fullscreen true :autoplay false :x nil}))
      "false and nil left out")
  (is (= "" (embed/allow {}))))
