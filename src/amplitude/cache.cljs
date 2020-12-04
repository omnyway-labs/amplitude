(ns amplitude.cache
  (:refer-clojure :exclude [get keys get-in])
  (:require
   ["@aws-amplify/cache"
    :default Cache]
   [amplitude.log :as log]
   [amplitude.util :as u]))

(defn as-storage-type [type]
  (condp = type
    :local   (.-localStorage js/window)
    :session (.-sessionStorage js/window)
    :nop))

(def config
  {:itemMaxSize 300000
   :defaultPriority 4})

(defn put [k v & {:keys [ttl priority]
                  :or {priority 2}}]
  (if ttl
    (.setItem Cache k (u/maybe-prn-str v)
              {:expires ttl :priority 2})
    (.setItem Cache k (u/maybe-prn-str v))))

(defn get
  ([k]
   (when-let [item (.getItem Cache k)]
     (u/maybe-read-str item)))
  ([k callback]
   (letfn [(as-fn [f]
             (if (fn? f) f #(identity (u/maybe-prn-str f))))]
     (.getItem Cache k
               (clj->js {:callback (as-fn callback)})))))

(defn delete! [k]
  (.removeItem Cache k))

(defn clear! []
  (.clear Cache))

(defn keys []
  (-> (.getAllKeys Cache)
      (js->clj)))

(defn size []
  (.getCacheCurSize Cache))

(defn get-in
  ([keys]
   (when-let [item (get (first keys))]
     (clojure.core/get-in item (rest keys))))
  ([keys callback]
   (when-let [item (get-in keys)]
     (callback item)
     item)))


(defn init! [& {:keys [storage]
                :or   {storage :local}}]
  (.configure Cache
              (->> (merge config
                          {:storage (as-storage-type storage)})
                   (clj->js))))
