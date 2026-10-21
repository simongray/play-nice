(ns dk.simongray.play-nice.runtime-test
  "The namespaces that keep state outside the values they're given: in
  localStorage, on a BroadcastChannel, by a Web Lock of the navigator, or
  in a watch on an atom. Each one is marked as runtime, as in
  (ns ^:runtime …), so that the rest stays functions of values and of the
  elements they're given. The check reads the sources, without their
  strings and comments."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]))

(def runtime-pattern
  "What only a runtime namespace may use."
  #"localStorage|BroadcastChannel|\.-locks|navigator\.locks|add-watch")

(defn code
  "The source `s` without its strings and comments."
  [s]
  (-> s
      (str/replace #"\"(?:\\.|[^\"\\])*\"" "\"\"")
      (str/replace #";[^\n]*" "")))

(defn runtime?
  "Whether the source `s` marks its namespace as runtime."
  [s]
  (boolean (re-find #"\(ns\s+\^:runtime\s" s)))

(deftest the-check-reads-code
  (is (re-find runtime-pattern (code "(add-watch a :k f)")))
  (is (re-find runtime-pattern (code "(some-> js/globalThis .-navigator .-locks)")))
  (is (nil? (re-find runtime-pattern (code "(def x \"in localStorage\") ; add-watch")))
      "but not a string or a comment")
  (is (runtime? "(ns ^:runtime a.b\n  \"Doc.\")"))
  (is (not (runtime? "(ns a.b)"))))

(deftest only-runtime-namespaces-keep-state
  (doseq [file  (file-seq (io/file "src"))
          :when (re-find #"\.clj[cs]?$" (.getName ^java.io.File file))
          :let  [s    (slurp file)
                 used (re-find runtime-pattern (code s))]
          :when used]
    (let [marked? (runtime? s)]
      (is marked? (str file " uses " used " but isn't marked as runtime")))))
