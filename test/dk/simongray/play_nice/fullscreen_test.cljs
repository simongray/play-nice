(ns dk.simongray.play-nice.fullscreen-test
  "Fullscreen on stand-ins for a player, its video and their document."
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.play-nice.fullscreen :as fullscreen]
            [dk.simongray.play-nice.test-support :refer [run]]))

(defn document
  "A stand-in for a document, as an EventTarget with no fullscreen
  element."
  []
  (doto (js/EventTarget.)
    (unchecked-set "fullscreenElement" nil)))

(defn element
  "A stand-in for an element of the document `doc` with the members of
  `m`, e.g. a requestFullscreen."
  [doc m]
  (let [^js el (js/EventTarget.)]
    (unchecked-set el "ownerDocument" doc)
    (doseq [[k v] m]
      (unchecked-set el (name k) v))
    el))

(defn iphone-video
  "A stand-in for a video on an iPhone, which goes into the system's
  player at webkitEnterFullscreen, if `allowed`."
  [doc allowed]
  (let [^js v (element doc {:readyState 1 :webkitSupportsFullscreen true})]
    (unchecked-set v "webkitEnterFullscreen"
                   #(if allowed
                      (do (set! (.-webkitDisplayingFullscreen v) true)
                          (.dispatchEvent v (js/Event. "webkitbeginfullscreen")))
                      (throw (js/DOMException. "" "InvalidStateError"))))
    (unchecked-set v "webkitExitFullscreen"
                   #(do (set! (.-webkitDisplayingFullscreen v) false)
                        (.dispatchEvent v (js/Event. "webkitendfullscreen"))))
    v))

(deftest entering
  (let [doc    (document)
        answer (fn [expected el & [opts]]
                 #(.then (fullscreen/enter! el opts)
                         (fn [a] (is (= expected a)))))]
    (run (answer {:fullscreen :element}
                 (element doc {:requestFullscreen #(js/Promise.resolve)}))
         (answer {:refused :not-allowed}
                 (element doc {:requestFullscreen #(js/Promise.reject (js/TypeError.))}))
         (answer {:fullscreen :video}
                 (element doc {})
                 {:video (iphone-video doc true)})
         (answer {:refused :not-allowed}
                 (element doc {})
                 {:video (iphone-video doc false)})
         (answer {:refused :no-metadata}
                 (element doc {})
                 {:video (doto (iphone-video doc true) (unchecked-set "readyState" 0))})
         (answer {:refused :not-supported} (element doc {}))
         ;; the lock's own answer differs in a browser, e.g. Firefox's
         #(.then (fullscreen/enter! (element doc {:requestFullscreen
                                                  (fn [] (js/Promise.resolve))})
                                    {:orientation :landscape})
                 (fn [{:keys [orientation] :as a}]
                   (is (= :element (:fullscreen a)))
                   (is (:refused orientation) "the answer of the lock too")))
         (answer {:refused :not-allowed}
                 (element doc {:requestFullscreen #(js/Promise.reject (js/TypeError.))})
                 {:orientation :landscape}))))

(deftest watching-and-leaving
  (let [states  (atom [])
        exits   (atom 0)
        ^js doc (document)
        player  (element doc {})
        video   (iphone-video doc true)
        opts    {:video video}
        stop!   (fullscreen/watch! player (partial swap! states conj) opts)]
    (set! (.-exitFullscreen doc) #(do (swap! exits inc) (js/Promise.resolve)))
    (run #(fullscreen/enter! player opts)
         #(fullscreen/exit! player opts)
         #(do (set! (.-fullscreenElement doc) player)
              (.dispatchEvent doc (js/Event. "fullscreenchange")))
         #(fullscreen/exit! player opts)
         #(is (= 1 @exits))
         #(do (set! (.-fullscreenElement doc) nil)
              (.dispatchEvent doc (js/Event. "fullscreenchange")))
         #(is (= [{:fullscreen false}
                  {:fullscreen :video}
                  {:fullscreen false}
                  {:fullscreen :element}
                  {:fullscreen false}]
                 @states))
         #(is (nil? (stop!)))
         #(do (.dispatchEvent doc (js/Event. "fullscreenchange"))
              (set! (.-fullscreenElement doc) player)
              (.dispatchEvent doc (js/Event. "fullscreenchange"))
              (is (= 5 (count @states)) "until it stops")))))

(deftest a-video-that-cannot-leave
  ;; as in the iOS simulator, for a video that has never played
  (let [doc    (document)
        player (element doc {})
        ^js v  (iphone-video doc true)
        opts   {:video v}]
    (set! (.-webkitExitFullscreen v) (constantly nil))
    (run #(fullscreen/enter! player opts)
         #(.then (fullscreen/exit! player opts)
                 (fn [a] (is (= {:fullscreen :video} a) "rather than false"))))))

(deftest locking-the-screen
  (run #(.then (fullscreen/lock-orientation! :landscape)
               (fn [{:keys [refused]}]
                 ;; Firefox refuses it outside fullscreen as SecurityError
                 (is (#{:not-supported :not-allowed} refused)
                     "in Node, or on a desktop outside fullscreen")))))
