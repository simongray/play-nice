(ns dk.simongray.play-nice.picture-in-picture-test
  "Picture-in-Picture on stand-ins for a video and its document."
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.play-nice.picture-in-picture :as pip]
            [dk.simongray.play-nice.test-support :refer [run]]))

(defn window
  "A stand-in for a PictureInPictureWindow of `width` by `height`."
  [width height]
  (doto (js/EventTarget.)
    (unchecked-set "width" width)
    (unchecked-set "height" height)))

(defn video
  "A stand-in for a video with metadata and a video track, in a document
  that allows Picture-in-Picture, with the attributes of `m` by their
  JavaScript names, and whose request returns what `f` returns."
  [f m]
  (let [^js v (js/EventTarget.)]
    (doseq [[k x] (merge {:ownerDocument #js {:pictureInPictureEnabled true}
                          :readyState    1
                          :videoWidth    320}
                         m)]
      (unchecked-set v (name k) x))
    (set! (.-requestPictureInPicture v) #(f v))
    v))

(defn refusing
  "A request that rejects with the DOMException named `s`."
  [s]
  (fn [_] (js/Promise.reject (js/DOMException. "" s))))

(deftest entering
  (let [opened (fn [_] (js/Promise.resolve (window 320 180)))
        answer (fn [expected v]
                 #(.then (pip/enter! v) (fn [a] (is (= expected a)))))]
    (run (answer {:width 320 :height 180} (video opened {}))
         (answer {:refused :not-allowed} (video (refusing "NotAllowedError") {}))
         (answer {:refused :not-allowed} (video (refusing "SecurityError") {}))
         (answer {:refused :disabled} (video opened {:disablePictureInPicture true}))
         (answer {:refused :no-video} (video opened {:videoWidth 0}))
         (answer {:refused :not-supported}
                 (video opened {:ownerDocument #js {:pictureInPictureEnabled false}}))
         (answer {:refused :not-supported} #js {:ownerDocument #js {}})
         (answer {:refused :invalid-state} (video (refusing "InvalidStateError") {})))))

(deftest entering-once-the-metadata-loads
  (let [^js v  (video (fn [_] (js/Promise.resolve (window 320 180)))
                      {:readyState 0})
        answer (pip/enter! v)]
    (is (pip/available? v) "before its metadata")
    (set! (.-readyState v) 1)
    (.dispatchEvent v (js/Event. "loadedmetadata"))
    (run #(.then answer (fn [a] (is (= {:width 320 :height 180} a)))))))

(deftest leaving
  (let [exits  (atom 0)
        ^js v  (video identity {})
        ^js d  (.-ownerDocument v)]
    (set! (.-exitPictureInPicture d) #(js/Promise.resolve (swap! exits inc)))
    (run #(.then (pip/exit! v) (fn [a] (is (= {:picture-in-picture false} a))))
         #(is (= 0 @exits) "nothing to leave")
         #(do (set! (.-pictureInPictureElement d) v)
              (is (pip/in-picture-in-picture? v))
              (pip/exit! v))
         #(is (= 1 @exits)))))

(deftest watching
  (let [states  (atom [])
        ^js v   (video identity {})
        ^js w   (window 320 180)
        event   (fn [kind]
                  (doto (js/Event. kind)
                    (unchecked-set "pictureInPictureWindow" w)))
        stop!   (pip/watch! v (partial swap! states conj))]
    (.dispatchEvent v (event "enterpictureinpicture"))
    (set! (.-width w) 400)
    (set! (.-height w) 225)
    (.dispatchEvent w (js/Event. "resize"))
    (.dispatchEvent v (event "leavepictureinpicture"))
    (.dispatchEvent w (js/Event. "resize"))
    (is (= [{:picture-in-picture false}
            {:picture-in-picture true :width 320 :height 180}
            {:picture-in-picture true :width 400 :height 225}
            {:picture-in-picture false}]
           @states)
        "and no resize after leaving")
    (is (nil? (stop!)))
    (.dispatchEvent v (event "enterpictureinpicture"))
    (is (= 4 (count @states)) "until it stops")))
