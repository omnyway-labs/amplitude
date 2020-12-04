(ns amplitude.storage
  (:refer-clojure :exclude [get list remove])
  (:require
   ["@aws-amplify/storage"
    :default Storage]
   ["@aws-amplify/core"
    :default Amplify]
   [amplitude.config :as config]
   [amplitude.log :as log]
   [amplitude.util :as u]))

(defn get [key callback-fn]
  (-> (.get Storage key)
      (.then (fn [url] (callback-fn url)))
      (.catch log/error)))

(defn remove [key]
  (-> (.remove Storage key)
      (.then log/info)
      (.catch log/error)))

(defn list [path callback-fn]
  (-> (.list Storage path)
      (.then (fn [data]
               (->> (js->clj data :keywordize-keys true)
                    (map :key)
                    (callback-fn))))))

(defn list-all [path collector-fn]
  (list path
        (fn [keys]
          (doseq [key keys]
            (get key
                 (fn [url]
                   (collector-fn [key url])))))))

(defn put-edn [key data]
  (-> (.put Storage key (u/clj->json data)
            (clj->js {:contentType "application/json"}))
      (.then log/info)
      (.catch log/error)))

(defn put [key data progress-fn on-success on-failure]
  (log/info :put {:key key})
  (-> (.put Storage key data
        (clj->js {"progressCallback"
                  (fn [progress]
                    (let [p (u/pct (-> progress .-loaded)
                                   (-> progress .-total))]
                      (progress-fn p)))}))
      (.then  #(on-success %))
      (.catch #(on-failure %))))

(defn init! []
  :ok)
