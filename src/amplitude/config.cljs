(ns amplitude.config
  (:require
   ["@aws-amplify/core"
    :default Amplify]))

(def debug? ^boolean goog.DEBUG)

(goog-define GQL_LOCAL false)

(defonce current (atom nil))

(defn lookup [& {:keys [format] :or {format :js}}]
  (condp = format
    :edn  @current
    :js   (clj->js @current)
    :json (clj->js @current)
    @current))

(defn override [cfg]
  (merge cfg
         {:aws_appsync_graphqlEndpoint "http://localhost:20002/graphql"
          :aws_appsync_apiKey          "da2-fakeApiId123456"}))

(defn coerce [cfg]
  (if (map? cfg)
    cfg
    (js->clj cfg :keywordize-keys true)))

(defn init! [config]
  (let [cfg (coerce config)]
    (if GQL_LOCAL
      (->> (override cfg)
         (reset! current))
      (reset! current cfg))
    (.configure Amplify config)))
