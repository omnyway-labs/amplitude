(ns amplitude.example
  (:require
   [reagent.core :as r]
   [amplitude.rest :as rest]
   [amplitude.graphql :as graphql]
   [amplitude.auth :as auth]
   [amplitude.config :as config]
   [amplitude.cache :as cache]
   [amplitude.storage :as storage]
   [amplitude.log :as log]
   ["/aws-exports.js" :default awsmobile]))

(defn view []
  [:h1 "Amplitude!"])

(defn init-view! []
  (r/render [view]
            (.getElementById js/document "app")))

(defn init []
  (init-view!)
  (config/init! awsmobile)
  (rest/init!)
  (cache/init!)
  (log/init!))
