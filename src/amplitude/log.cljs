(ns amplitude.log
  (:require
   [amplitude.util :as u]
   ["aws-amplify" :refer [Logger]]))

(def logger (Logger. "amplitude" "DEBUG"))

(defonce stash (atom nil))

(defn info
  ([msg]
   (.info logger msg))
  ([key msg]
   (cond
     (and (map? key) (map? msg))
     (info (pr-str (merge key msg)))

     (and (keyword? key) (map? msg))
     (info (pr-str {key msg}))

     (or (keyword? key) (string? key))
     (info (u/clj->json {key msg}))

     :else (info msg))))

(defn debug
  ([msg]
   (.debug logger msg))
  ([key msg]
   (.debug logger (u/clj->json {key msg}))))

(defn error
  ([msg] (.error logger msg))
  ([key msg]
   (.error logger (u/clj->json {key msg}))))

(defn stash!
  ([] @stash)
  ([thing] (reset! stash thing)))

(defn init! []
  :ok)
