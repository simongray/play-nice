(ns dk.simongray.play-nice.tabs-test
  "One player at a time, where there are no Web Locks."
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.play-nice.tabs :as tabs]))

(deftest without-locks
  (is (nil? ((tabs/attach! (js/EventTarget.) "app" {:window #js {}})))))
