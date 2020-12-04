(ns amplitude.auth
  (:require
   [clojure.walk :as walk]
   ["@aws-amplify/core"
    :refer [Hub]
    :default Amplify]
   ["@aws-amplify/auth" :default Auth]
   [amplitude.log :as log]
   [amplitude.config :as config]))

(defn sign-in [{:keys [username password callback-fn on-error]}]
  (.. Auth
      (signIn username password)
      (then
       (fn [response]
         (let [attrs    (-> (aget response "attributes")
                            (js->clj :keywordize-keys true))
               username (aget response "username")
               mfa      (aget response "preferredMFA")
               token    (-> (aget response "signInUserSession")
                            (aget "accessToken")
                            (aget "jwtToken"))
               data     (merge attrs
                               {:username username
                                :token token
                                :mfa mfa})]
           (callback-fn data))))
      (catch
          (fn [response]
            (let [err (-> (js->clj response)
                          (walk/keywordize-keys))]
              (on-error err))))))

(defn sign-out []
  (.. Auth
      (signOut)
      (then (fn [response] (log/info :auth-sign-out)))
      (catch (fn [response] (log/error :error)))))

(defn setup-hub-listener []
  (.listen Hub "auth"
           (fn [data]
             (.-event (.-payload data)))))

(defn fetch-user-info []
  (let [user (aget Auth "user")
        username (aget user "username")
        token (-> (aget user "signInUserSession")
                  (aget "idToken")
                  (aget "jwtToken"))
        attrs (-> (aget user "attributes")
                  (js->clj)
                  (walk/keywordize-keys))]
    (merge attrs
           {:token token :username username})))

(defn get-token []
  (:token (fetch-user-info)))

(defn username []
  (:username (fetch-user-info)))
