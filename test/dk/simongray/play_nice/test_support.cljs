(ns dk.simongray.play-nice.test-support
  "Helpers for the async tests: running steps one after another, and
  waiting for an element's next event."
  (:require [clojure.test :refer [async is]]))

(defn run
  "Run the steps `fs` one after another as an async test. Each step is a
  function of no arguments that returns a value or a promise.

  A deftest runs only the async test that it returns, so it calls `run`
  once, as its last form."
  [& fs]
  (async done
    (-> (reduce (fn [p f] (.then p (fn [_] (f)))) (js/Promise.resolve nil) fs)
        (.catch (fn [e] (is false (str "rejected: " e))))
        (.finally done))))

(defn next-event
  "A promise of the next event named `event` at the `element`."
  [^js element event]
  (js/Promise. (fn [resolve _]
                 (.addEventListener element event resolve #js {:once true}))))
