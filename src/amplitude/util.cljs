(ns amplitude.util
  (:require
   [clojure.string :as str]
   [clojure.walk :as walk]
   [cljs.reader :as reader]))

(defn remove-from-end [s end]
  (if (str/ends-with? s end)
    (subs s 0 (- (count s) (count end)))
    s))

(defn fake? [thing]
  (cond
    (boolean? thing) false
    (int? thing)     false
    (nil? thing)     true
    (empty? thing)   true
    :else            false))

(defn remove-nils [m]
  (reduce-kv #(if-not (fake? %3)
                (assoc %1 %2 %3)
                %1)
             {}
             m))

(defn clj->json [edn-map]
  (.stringify js/JSON (clj->js edn-map)))

(defn json->clj [json]
  (js->clj (.parse js/JSON json)))

(defn transform-keys [t coll]
  (let [f (fn [[k v]] [(t k) v])]
    (walk/postwalk (fn [x]
                     (if (map? x)
                       (into {} (map f x))
                       x))
                   coll)))

(defn ->kebab-case [k]
  (str/replace (name k) #"_" "-"))

(defn ->snake-case [k]
  (str/replace (name k) #"-" "_"))

(defn snake-kw [k]
  (keyword (->snake-case k)))

(defn kebab-kw [k]
  (keyword (->kebab-case k)))

(defn kebab-map [m]
  (transform-keys kebab-kw m))

(defn snake-map [m]
  (transform-keys snake-kw m))

(defn read-string [s]
  (try
    (when s
      (reader/read-string s))
    (catch js/Object e s)))

(defn- maybe-read-str [s]
  (when-let [st (read-string s)]
    (if (symbol? st) s st)))

(defn- maybe-prn-str [s]
  (if (string? s) s (pr-str s)))

(defn obj->clj
  "Get a map of a javascript object"
  [obj]
  (if (goog.isObject obj)
    (-> (fn [result key]
          (let [v (goog.object/get obj key)]
            (if (= "function" (goog/typeOf v))
              result
              (assoc result key (obj->clj v)))))
        (reduce {} (.getKeys goog/object obj)))
    obj))

(defn pct [x y]
  (.round js/Math
          (* (/ x y) 100.0)))

(defn pascalize [s]
  (->> (str/split (name s) #"-")
       (map str/capitalize)
       (str/join "")))

(defn camelize [s]
  (let [words (str/split (name s) #"[\s_-]+")]
    (->> (map str/capitalize (rest words))
         (cons (str/lower-case (first words)))
         (str/join ""))))

(defn as-edn [obj]
  (js->clj obj :keywordize-keys true))

(defn dashed [& kws]
  (->> (map name kws)
       (interpose "-")
       (apply str)))
