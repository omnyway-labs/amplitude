(ns amplitude.graphql.subscription
  (:require
   [clojure.string :as str]
   ["@aws-amplify/api"
    :as api
    :default API]
   [camel-snake-kebab.core :as csk]
   [amplitude.log :as log]
   [amplitude.util :as u]))

(defonce subscriptions (atom nil))
(defonce last-updated (atom nil))

(defn- parse-response [res sub-id]
  (-> (js->clj res :keywordize-keys true)
      (u/kebab-map)
      (get-in [:value :data (csk/->camelCaseKeyword sub-id)])))

(defn lock! [state query sub-id]
  (swap! subscriptions
         assoc sub-id {:sub-id sub-id
                       :state  state
                       :query  query}))

(defn- duplicate? [sub-id data]
  (let [recent (sub-id @last-updated)]
    (or (= recent data)
        (when data
          (swap! last-updated assoc sub-id data)
          false))))

(defn subscribe! [op sub-id callback]
  (-> (.. API
          (graphql op)
          (subscribe
            (fn [record]
              (let [data (parse-response record sub-id)]
                (when-not (duplicate? sub-id data)
                  (callback data))))
            #(log/error ::subscription-error %)))))

(defn unsubscribe!
  ([] (doseq [[on _] @subscriptions]
        (unsubscribe! on)))
  ([on]
   (when-let [sub (@subscriptions on)]
     (log/info :unsubscribe on)
     (.unsubscribe (:state sub))
     (swap! subscriptions dissoc on))))

(defn list-current []
  (->> (map (fn [[sub-id {:keys [state query]}]]
              [sub-id {:state (get (u/obj->clj state) "_state")
                       :query query}])
            @subscriptions)
       (into {})))
