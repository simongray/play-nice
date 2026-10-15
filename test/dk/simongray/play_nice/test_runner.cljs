(ns dk.simongray.play-nice.test-runner
  "A runner of the browser tests that prints the report into the page and
  the summary into its title, e.g. \"0 failures, 0 errors\". It replaces
  the runner of shadow-cljs, whose display can't be compiled with advanced
  optimizations."
  ;; recompiled at every build, since the test namespaces of the build are
  ;; compiled into it, as in shadow-cljs's own runner
  {:dev/always true}
  (:require [cljs.test :as test]
            [shadow.test :as st]
            [shadow.test.env :as env]))

(defmethod test/report [::test/default :end-run-tests]
  [{:keys [fail error] :as counters}]
  (set! (.-title js/document) (str fail " failures, " error " errors"))
  ;; for test/simulator.mjs, which can't read the page of a simulator
  (when (= "?report" (.-search js/location))
    (js/fetch "/report" #js {:method "POST"
                             :body   (str (.-textContent (js/document.querySelector "pre"))
                                          "\n" (.-title js/document))})))

(defn start
  "Run every test namespace of the build."
  []
  (-> (env/get-test-data)
      (env/reset-test-data!))
  (st/run-all-tests))

(defn stop
  "Call `done` when the tests may be reloaded."
  [done]
  (done))

(defn ^:export init
  "Print into a pre element of the page, and run the tests."
  []
  (let [pre     (js/document.createElement "pre")
        append! #(.appendChild pre (js/document.createTextNode %))]
    (set! (.-title js/document) "running")
    (set! *print-newline* true)
    (.appendChild js/document.body pre)
    (set-print-fn! append!)
    (set-print-err-fn! append!)
    (start)))
