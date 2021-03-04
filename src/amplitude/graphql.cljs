(ns amplitude.graphql
  (:refer-clojure :exclude [get list find delete])
  (:require
   [clojure.string :as str]
   ["@aws-amplify/api"
    :as api
    :default API]
   [camel-snake-kebab.core :as csk]
   [amplitude.log :as log]
   [amplitude.config :as config]
   [amplitude.util :as u]
   [amplitude.cache :as cache]
   [amplitude.graphql.schema :as schema]
   [amplitude.graphql.subscription :as sub]))

(defn method-id [op entity]
  (-> (schema/method-name op entity)
      (csk/->camelCase)
      (keyword)))

(defn- xform-output [data op entity]
  (-> (js->clj data :keywordize-keys true)
      (u/kebab-map)
      (get-in [:data
               (if (= op :search)
                 (csk/->camelCaseKeyword entity)
                 (method-id op entity))])))

(defn- xform-input [entity param]
  (letfn [(connection? [k]
            (str/ends-with? (name k) "*"))
          (as-input-key [entity k]
            (if (connection? k)
              (->> (u/remove-from-end (name k) "*")
                   (u/pascalize)
                   (str (u/camelize entity))
                   keyword)
              (-> (u/->snake-case k)
                  (keyword))))]
    (reduce-kv #(assoc %1 (as-input-key entity %2) %3)
               {} (u/remove-nils param))))

(defn- xform-filter [expr]
  (cond
    (= expr :active)
    {:inactive {:ne true}}

    (vector? expr)
    (let [[k v] expr]
      {k {:eq v}})

    (map? expr) (u/snake-map expr)))

(defn xform-param [op entity {:keys [input filter limit]
                              :as   param}]
  (cond
    (= op :get) input
    (= op :search)
    (u/remove-nils
     (merge param
            {:filter (when filter (xform-filter filter))
             :limit  (or limit 100)}))

    :else
    (u/remove-nils
     {:input  (when input  (xform-input entity input))
      :filter (when filter (xform-filter filter))
      :limit  (or limit 100)})))

(defn- invoke* [query-str param on-success on-error]
  (let [operation (if (empty? param)
                    (api/graphqlOperation query-str)
                    (api/graphqlOperation query-str (clj->js param)))]
    (-> (.. API (graphql operation))
        (.then on-success)
        (.catch on-error))))

(defn default-on-error [xs]
  (log/error ::gql-error xs))

(defn- invoke
  [{:keys [op query entity param on-success on-error]
    :or   {on-error default-on-error}}]
  (let [param (xform-param op entity param)]
    (invoke* query
             param
             (fn [data]
               (->> (xform-output data op entity)
                    (on-success)))
             (fn [res]
               (let [{:keys [errors]} (u/as-edn res)]
                 (on-error
                  {:errors (or errors res)
                   :op     op
                   :query  query
                   :entity entity
                   :param  param}))))))

(defn- as-cache-key [entity]
  (-> (u/dashed :list entity)
      (str "s")
      (keyword)))

(defn- delete-cache! [entity]
  (-> (as-cache-key entity)
      (cache/delete!)))

(defn create!
  [entity {:keys [input on-create
                  invalidate-cache? shape
                  on-error]
           :or   {on-create         log/stash!
                  invalidate-cache? true
                  on-error          log/error}}]
  (invoke {:op         :create
           :entity     entity
           :query      (schema/mutation :create entity shape)
           :param      {:input input}
           :on-success (fn [record]
                         (when invalidate-cache?
                           (delete-cache! entity))
                         (on-create record))
           :on-error   on-error}))

(defn list
  [entity {:keys [on-list cache? filter shape limit]
           :or   {cache?  false
                  filter  :active
                  on-list log/stash!}}]
  (let [cache-key   (as-cache-key entity)
        cached-data (cache/get cache-key)
        query       (schema/list entity shape)]
    (if (and cache? (not (empty? cached-data)))
      (on-list cached-data)
      (invoke {:op         :list
               :entity     entity
               :query      query
               :param      {:filter filter
                            :limit  limit}
               :on-success (fn [{:keys [items]}]
                             (when cache?
                               (cache/put cache-key items))
                             (on-list items))}))))

(defn search
  [entity {:keys [key value on-search shape
                  key-type
                  sort-direction
                  filter query-field
                  limit next-token]
           :or   {on-search      log/stash!
                  sort-direction "DESC"
                  key-type       :String
                  limit          500}}]
  (when (and key value query-field)
    (invoke {:op         :search
             :query      (schema/search entity query-field key {:shape    shape
                                                                :key-type key-type})
             :entity     query-field
             :param      {(u/->snake-case key) value
                          :sortDirection            sort-direction
                          :filter                   filter
                          :limit                    limit
                          :nextToken                next-token}
             :on-success (fn [{:keys [items]}]
                           (on-search items))})))

(defn get
  [entity {:keys [input on-get invalidate-cache? shape
                  on-error]
           :or   {on-get log/stash!
                  on-error default-on-error}}]
  (invoke {:op         :get
           :entity     entity
           :query      (schema/get entity shape)
           :param      {:input input}
           :on-success (fn [record]
                         (when invalidate-cache?
                           (delete-cache! entity))
                         (on-get record))
           :on-error   on-error}))

(defn delete!
  [entity {:keys [input on-delete
                  invalidate-cache?
                  shape]
           :or   {on-delete         log/stash!
                  invalidate-cache? true}}]
  (invoke {:op         :delete
           :entity     entity
           :query      (schema/mutation :delete entity shape)
           :param      {:input input}
           :on-success (fn [record]
                         (when invalidate-cache?
                           (delete-cache! entity))
                         (on-delete record))}))

(defn update!
  [entity {:keys [input filter
                  on-update
                  invalidate-cache?
                  shape]
           :or   {invalidate-cache? true
                  on-update         log/stash!}}]
  (invoke {:op         :update
           :entity     entity
           :param      {:input input}
           :query      (schema/mutation :update entity shape)
           :on-success (fn [record]
                         (when invalidate-cache?
                           (delete-cache! entity))
                         (on-update record))}))

(defn upsert!
  [entity {:keys [input filter
                  on-update on-create
                  shape]
           :or   {on-update log/stash!
                  on-create log/stash!}}]
  (invoke {:op         :list
           :entity     entity
           :query      (schema/list entity [:id])
           :param      {:filter filter}
           :on-success (fn [{:keys [items]}]
                         (let [{:keys [id]} (first items)]
                           (if id
                             (update! entity
                                      {:input     (merge input {:id id})
                                       :on-update on-update
                                       :shape     shape})
                             (create! entity
                                      {:input     input
                                       :on-create on-create
                                       :shape     shape}))))}))

(defn find-or-create!
  [entity {:keys [input filter
                  on-find on-create
                  shape]
           :or   {on-find   log/stash!
                  on-create log/stash!}}]
  (invoke {:op         :list
           :entity     entity
           :query      (schema/list entity (or shape [:id]))
           :param      {:filter filter}
           :on-success (fn [{:keys [items]}]
                         (if (empty? items)
                           (create! entity
                                    {:input     input
                                     :on-create on-create
                                     :shape     shape})
                           (on-find (first items))))}))

(defn subscribe! [sub-id  {:keys [input on-change shape]}]
  (if (empty? input)
    (let [query (schema/subscription sub-id shape)
          op    (api/graphqlOperation query)]
      (-> (sub/subscribe! op sub-id on-change)
          (sub/lock! query sub-id)))
    (let [query (->> (key (first input))
                     (schema/subscription sub-id shape))
          op    (->> (clj->js (u/snake-map input))
                     (api/graphqlOperation query))]
      (-> (sub/subscribe! op sub-id on-change)
          (sub/lock! query sub-id)))))

(defn unsubscribe-all []
  (sub/unsubscribe!))

(defn unsubscribe!
  ([] (sub/unsubscribe!))
  ([sub-id] (sub/unsubscribe! sub-id)))

(defn list-subs []
  (sub/list-current))

(defn resolve! [mutation-id {:keys [input-schema input
                                    on-resolve on-error
                                    shape]}]
  (let [query      (schema/custom-mutation mutation-id input-schema shape)
        param      (clj->js (u/snake-map input))
        op         (api/graphqlOperation query param)
        on-resolve (or on-resolve identity)
        on-error   (or on-error identity)]
    (-> (.. API (graphql op))
        (.then
         (fn [res]
           (-> (u/as-edn res)
               (get-in [:data (csk/->camelCaseKeyword mutation-id)])
               (u/kebab-map)
               (on-resolve))))
        (.catch
         (fn [res]
           (let [{:keys [errors]} (u/as-edn res)]
             (on-error (-> errors first :message))
             (log/error ::resolve {:errors errors
                                   :res    res})))))))
