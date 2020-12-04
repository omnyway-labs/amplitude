(ns amplitude.rest
  (:refer-clojure :exclude [get])
  (:require
   [clojure.walk :as walk]
   ["@aws-amplify/api" :default API]
   [amplitude.log :as log]
   [amplitude.auth :as auth]
   [amplitude.config :as config]))

(def cors-headers
  {"Access-Control-Allow-Origin"  "*"
   "Access-Control-Allow-Headers" "Access-Control-Allow-Origin"})

(defn api-config []
  (-> (config/lookup :format :edn)
      :aws_cloud_logic_custom))

(defn get-api-id
  ([] (-> (first (api-config))
          :name))
  ([api-id]
   (->> (api-config)
        (filter #(= (:name %) api-id))
        (first))))

(defn parse-response [response]
  (-> (js->clj response :keywordize-keys true)
      :data))

(defn make-payload [body]
  (clj->js {:headers  (merge cors-headers
                             {"Authorization" (auth/get-token)})
            :body     (clj->js body)
            :response true}))

(defn post
  ([path body success-handler]
   (post path body success-handler :api-response-error))
  ([path body success-handler error-handler]
   (-> (.post API (get-api-id) path (make-payload body))
       (.then
        (fn [response]
          (->> (parse-response response)
               (success-handler))))
       (.catch
        (fn [response]
          (let [message (.-message response)]
            (log/error :post-error message)
            (error-handler message)))))))

(defn get
  ([path success-handler]
   (get path success-handler log/error))
  ([path success-handler error-handler]
   (-> (.get API (get-api-id) path (make-payload {}))
       (.then
        (fn [response]
          (->> (parse-response response)
               (success-handler))))
       (.catch
        (fn [response]
          (let [message (.-message response)]
            (log/error :post-error message)
            (error-handler message)))))))

(defn init! []
  (.configure API (js->clj (api-config))))
