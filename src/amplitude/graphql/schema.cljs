(ns amplitude.graphql.schema
  (:refer-clojure :exclude [get list update])
  (:require
   [clojure.walk :as walk]
   [clojure.string :as str]
   [camel-snake-kebab.core :as csk]
   [graphql-query.core :refer [graphql-query]]
   [amplitude.log :as log]))

(defn caps [s]
  (str/capitalize (name s)))

(defn transform-keys [f coll]
  (walk/postwalk (fn [x]
                   (if (keyword? x)
                     (f x)
                     x))
                 coll))

(defn as-shape [fields]
  (letfn [(xform [thing]
            (if (keyword? thing)
              (keyword
               (str/replace (name thing) #"-" "_"))
              thing))]
    (transform-keys xform fields)))

(defn method-name [op entity]
  (if (= op :list)
    (str "list" (caps entity) "s")
    (str (name op) (caps entity))))

(defn- op-name [op entity]
  (->> (method-name op entity)
       (csk/->PascalCaseString)))

(defn- method-id [op entity]
  (->> (op-name op entity)
       (csk/->camelCaseKeyword)))

(defn get [entity shape]
  (when-not (empty? shape)
    (graphql-query
     {:operation {:operation/type :query
                  :operation/name (op-name :get entity)}
      :variables [{:variable/name :$id
                   :variable/type :ID!}]
      :queries   [[(method-id :get entity)
                   {:id :$id}
                   (as-shape shape)]]})))

(defn- as-filter-model [entity]
  (keyword (str "Model"
                (csk/->PascalCase (name entity))
                "FilterInput")))

(defn search [entity query-field key
             {:keys [key-type shape]
              :or   {key-type :String
                     shape    [:id]}}]
  (when-not (empty? shape)
    (let [variable-id (->> (csk/->snake_case_string key)
                           (str "$")
                           (keyword))]
      (graphql-query
       {:operation {:operation/type :query
                    :operation/name (csk/->PascalCaseString query-field)}
        :variables [{:variable/name variable-id
                     :variable/type key-type}
                    {:variable/name :$sortDirection
                     :variable/type :ModelSortDirection}
                    {:variable/name :$filter
                     :variable/type (as-filter-model entity)}
                    {:variable/name :$limit
                     :variable/type :Int}
                    {:variable/name :$nextToken
                     :variable/type :String}]
        :queries   [[(csk/->camelCaseKeyword query-field)
                     {(csk/->snake_case_string key) variable-id
                      :sortDirection                :$sortDirection
                      :nextToken                    :$nextToken
                      :limit                        :$limit
                      :filter                       :$filter}
                     [[:items (as-shape shape)] :nextToken]]]}))))

(defn list [entity shape]
  (when-not (empty? shape)
    (graphql-query
     {:operation {:operation/type :query
                  :operation/name (op-name :list entity)}
      :variables [{:variable/name :$filter
                   :variable/type (as-filter-model entity)}
                  {:variable/name :$limit
                   :variable/type :Int}
                  {:variable/name :$nextToken
                   :variable/type :String}]
      :queries   [[(method-id :list entity)
                   {:filter    :$filter
                    :nextToken :$nextToken
                    :limit     :$limit}
                   [[:items (as-shape shape)]]]]})))

(defn mutation [op entity shape]
  (letfn [(as-input-model [op entity]
            (->> (map name [op entity :input!])
                 (str/join "-" )
                 (apply str)
                 (csk/->PascalCaseKeyword)))]
    (when-not (empty? shape)
      (graphql-query
       {:operation {:operation/type :mutation
                    :operation/name (op-name op entity)}
        :variables [{:variable/name :$input
                     :variable/type (as-input-model op entity)}]
        :queries   [[(method-id op entity)
                     {:input :$input}
                     (as-shape shape)]]}))))

(defn subscription
  ([sub-id shape]
   (let [op-name  (csk/->PascalCase sub-id)]
     (when-not (empty? shape)
       (graphql-query
        {:operation {:operation/type :subscription
                     :operation/name op-name}
         :queries   [[(csk/->camelCase sub-id)
                      (as-shape shape)]]}))))
  ([sub-id shape key]
   (let [op-name  (csk/->PascalCase sub-id)
         ;; Snake case turns :productId into product_id which doesn't match the API field names. Use camelCase for now.
         key      (csk/->camelCase key) #_(csk/->snake_case_keyword key)
         variable (keyword (str "$" (name key)))]
     (when-not (empty? shape)
       (graphql-query
        {:operation {:operation/type :subscription
                     :operation/name op-name}
         :variables [{:variable/name variable
                      :variable/type :String}]
         :queries   [[(csk/->camelCase sub-id)
                      {key variable}
                      (as-shape shape)]]})))))

(defn make-variables [input]
  (for [[k v] input]
    {:variable/name (keyword (str "$" (csk/->snake_case_string k)))
     :variable/type (if (keyword? v)
                      v
                      (keyword (str "[" (name (first v)) "]")))}))

(defn make-param [input]
  (->> (map (fn [[k _]]
              [(csk/->snake_case_keyword k)
               (keyword (str "$" (csk/->snake_case_string k)))])
            input)
       (into {})))

(defn custom-mutation [mutation-id input-schema shape]
  (let [op-name   (csk/->PascalCaseKeyword mutation-id)
        method-id (csk/->camelCaseKeyword mutation-id)
        param     (make-param input-schema)]
    (graphql-query
      {:operation {:operation/type :mutation
                   :operation/name op-name}
       :variables (make-variables input-schema)
       :queries   [[method-id
                    param
                    (as-shape shape)]]})))
