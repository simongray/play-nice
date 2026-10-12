(ns dk.simongray.play-nice.keys
  "The member names of the specs' dictionaries as Clojure keys, e.g.
  :seek-time for seekTime, and maps turned into JavaScript objects and
  back.

  One rule converts a name either way, so a member that the library
  doesn't know yet passes through. It holds because no member name in
  these specs has two capitals in a row or a digit."
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
  "The JavaScript value of `x`: a map becomes an object of its member names
  without its nil values, a keyword its name, and a sequential an array."
  [x]
  (cond
    ;; WebIDL reads a null string as "null", while a missing member takes
    ;; its default
    (map? x)        (->> x
                         (filter (comp some? val))
                         (mapcat (fn [[k v]] [(keyword->member k) (->js v)]))
                         (apply js-obj))
    (keyword? x)    (name x)
    (sequential? x) (into-array (map ->js x))
    :else           x))

(defn ->clj
  "The Clojure value of the JavaScript `x`: an object becomes a map of
  kebab-case keywords and an array a vector, and the values of the `enums`
  keys become keywords, e.g. those of #{:action}."
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
