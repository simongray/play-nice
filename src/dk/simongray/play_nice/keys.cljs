(ns ^:no-doc dk.simongray.play-nice.keys
  "Conversion between Clojure maps with kebab-case keys and the JavaScript
  objects of the specs' dictionaries, e.g. {:seek-time 12} and
  {seekTime: 12}.

  A single rule converts each name both ways, so a member that the
  library doesn't know yet passes through too. The rule works because no
  member name in these specs has two capitals in a row or a digit."
  (:require [clojure.string :as str]))

(defn member->keyword
  "The kebab-case keyword of the camelCase member name `s`, e.g. :seek-time
  for \"seekTime\"."
  [s]
  (keyword (str/replace s #"[A-Z]" #(str "-" (str/lower-case %)))))

(defn keyword->member
  "The camelCase member name of the kebab-case keyword `k`, e.g. \"seekTime\"
  for :seek-time."
  [k]
  (str/replace (name k) #"-([a-z])" #(str/upper-case (second %))))

(defn ->js
  "Convert `x` to JavaScript:

  - a map to an object with camelCase member names, without nil values
  - a keyword to its name, e.g. \"seekto\" for :seekto
  - a sequential to an array"
  [x]
  (cond
    ;; WebIDL turns a null string into "null", but a missing member gets its
    ;; default
    (map? x)        (->> x
                         (filter (comp some? val))
                         (mapcat (fn [[k v]] [(keyword->member k) (->js v)]))
                         (apply js-obj))
    (keyword? x)    (name x)
    (sequential? x) (into-array (map ->js x))
    :else           x))

(defn ->clj
  "Convert the JavaScript `x` to Clojure, with the values of the `enums`
  keys as keywords, e.g. of #{:action}. An object becomes a map with
  kebab-case keys, and an array a vector."
  ([x]
   (->clj x #{}))
  ([x enums]
   (cond
     (array? x)  (mapv #(->clj % enums) x)
     (object? x) (into {}
                       (map (fn [member]
                              (let [k (member->keyword member)
                                    v (->clj (unchecked-get x member) enums)]
                                [k (cond-> v (enums k) keyword)])))
                       (js-keys x))
     :else       x)))
