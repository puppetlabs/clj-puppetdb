(ns clj-puppetdb.http
  (:require [cemerick.url :refer [url-encode map->query]]
            [cheshire.core :as json]
            [clj-puppetdb.schema :refer [Client]]
            [clj-puppetdb.util :refer [file?]]
            [puppetlabs.http.client.sync :as http]
            [schema.core :as s]))

;; TODO:
;;   - Validate schema for GET params. The GetParams schema
;;     exists, but needs work before it can be used.

(defn make-https-client
  [^String host {:keys [ssl-ca-cert ssl-cert ssl-key]}]
  {:pre [(every? file? [ssl-ca-cert ssl-cert ssl-key])
         (.startsWith host "https://")]
   :post [(s/validate Client %)]}
  {:host host
   :opts {:ssl-ca-cert ssl-ca-cert
          :ssl-cert ssl-cert
          :ssl-key ssl-key}})

(defn make-http-client
  [^String host]
  {:pre [(.startsWith host "http://")]
   :post [(s/validate Client %)]}
  {:host host
   :opts {}})

(s/defn ^:always-validate GET
  "Make a GET request using the given PuppetDB client, returning the results
  as a lazy sequence of maps with keywordized keys.

  The `path` argument must be a URL-encoded string.

  You may provide a set of querystring parameters as a map. These will be url-encoded
  automatically and added to the path."
  ([client :- Client ^String path]
     #_(println "GET:" path) ;; uncomment this to watch queries
     (let [{:keys [host opts]} client]
       (-> (http/get (str host path) (assoc opts :as :text))
           :body
           (json/decode keyword))))
  ([client path params]
     (let [query-params (map->query params)
           new-path (str path "?" query-params)]
       (GET client new-path))))
