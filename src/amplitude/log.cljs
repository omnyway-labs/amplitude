(ns amplitude.log
  (:require-macros
   [lambdaisland.glogi :as l])
  (:require
   [amplitude.util :as u]
   [lambdaisland.glogi :as log]
   [lambdaisland.glogi.console :as glogi-console]))

(defonce stash (atom nil))

(defn info
  ([msg] (log/info :lambdaisland.glogi/logger msg))
  ([key msg]
   (log/info key msg)))

(defn error
  ([msg] (log/error :default msg))
  ([key msg]
   (log/error key msg)))

(defn debug
  ([msg] (log/debug :default msg))
  ([key msg]
   (log/debug key msg)))

(defn spy [expr] (log/spy expr))

(defn stash!
  ([] @stash)
  ([thing] (reset! stash thing)))

(defn init!
  ([] (init! {}))
  ([log-levels]
   (glogi-console/install!)
   (log/set-levels
    (merge {:glogi/root :info} log-levels))))
