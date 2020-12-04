(ns amplitude.hub
  (:require
   ["@aws-amplify/core"
    :refer [Hub]]))

(defn init! []
  (.listen Hub "auth"
           (fn [data]
             (.-event (.-payload data)))))
