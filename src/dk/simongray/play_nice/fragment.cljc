(ns dk.simongray.play-nice.fragment
  "The times that a link points at: the temporal media fragment of a media
  file, e.g. #t=73,133, and the t of a page, e.g. ?t=1m13s.

  A media fragment addresses the file, which every engine starts at the
  fragment's start. A page's t is a convention of its own, e.g. for the
  key moments of a video in Google's search, and must stay off the URL of
  the file itself, since podcast hosts count it as another file.

  The times are numbers of seconds, which are doubles on the JVM."
  (:refer-clojure :exclude [time])
  (:require [clojure.string :as str]))

(defn- number
  "The number that the decimal digits `s` write, e.g. 73.5 for \"73.5\"."
  [s]
  #?(:clj  (Double/parseDouble s)
     :cljs (js/parseFloat s)))

;; W3C Media Fragments URI 1.0 (basic), §4.2.1, Normal Play Time
(defn npt
  "The seconds of the Normal Play Time `s`, e.g. 73.5 for \"73.5\" and 120
  for \"0:02:00\", or nil where `s` isn't one. Minutes and seconds take two
  digits each."
  [s]
  (when-let [[_ h m sec] (re-matches #"(?:(?:(\d+):)?([0-5]\d):)?(\d+(?:\.\d*)?)"
                                     (str s))]
    (when (or (nil? m) (re-matches #"[0-5]\d(?:\.\d*)?" sec))
      (+ (* 3600 (number (or h "0")))
         (* 60 (number (or m "0")))
         (number sec)))))

(defn interval
  "The interval of the value `s` of a t, a map of its :start and :end in
  seconds, e.g. {:start 10 :end 20} for \"npt:10,20\", or nil where `s` is
  invalid. The start is 0 where the value leaves it out, and the end is
  left out where it does."
  [s]
  (let [s (str/replace (str s) #"^npt:" "")]
    (when-let [[_ start end] (re-matches #"([^,]*)(?:,(.*))?" s)]
      (let [start* (if (str/blank? start) 0.0 (npt start))
            end*   (when end (npt end))]
        (when (and start*
                   (or (nil? end) end*)
                   (not (and (str/blank? start) (nil? end)))
                   (or (nil? end*) (< start* end*)))
          (cond-> {:start start*}
            end* (assoc :end end*)))))))

;; URLDecoder reads + as a space, which decodeURIComponent doesn't
(defn- decode
  "The percent-decoded `s`, which throws where `s` doesn't decode."
  [s]
  #?(:clj  (java.net.URLDecoder/decode (str/replace s "+" "%2B") "UTF-8")
     :cljs (js/decodeURIComponent s)))

(defn- pairs
  "The name-value pairs of the fragment or query `s`, percent-decoded, as
  the spec's §5.1.1 parses them, leaving out the pairs that don't decode."
  [s]
  (keep (fn [pair]
          (let [[name value] (str/split pair #"=" 2)]
            (try
              [(decode name) (decode (or value ""))]
              (catch #?(:clj Exception :cljs :default) _ nil))))
        (str/split (str s) #"&")))

;; §5.1.2: the last valid occurrence of a dimension is the one that counts
(defn time
  "The interval of the temporal media fragment of `url`, a map of its
  :start and :end in seconds, or nil where it has no valid one, e.g.

      (time \"https://cdn.example/272.mp3#t=npt:73,133\")
      ;; => {:start 73 :end 133}"
  [url]
  (let [[_ fragment] (re-find #"#(.*)$" (str url))]
    (->> (pairs fragment)
         (keep (fn [[name value]] (when (= "t" name) (interval value))))
         last)))

(defn link-time
  "The seconds that the t of the page `url` points at, in seconds, e.g. 73
  for ?t=73 and ?t=1m13s, or nil where it has none."
  [url]
  (let [[_ query] (re-find #"\?([^#]*)" (str url))
        value     (some (fn [[name value]] (when (= "t" name) value))
                        (reverse (pairs query)))]
    (when value
      (if-let [[_ h m s] (re-matches #"(?:(\d+)h)?(?:(\d+)m)?(?:(\d+(?:\.\d+)?)s?)?"
                                     value)]
        (when-not (str/blank? value)
          (+ (* 3600 (number (or h "0")))
             (* 60 (number (or m "0")))
             (number (or s "0"))))
        (npt value)))))
