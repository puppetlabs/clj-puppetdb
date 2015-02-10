(ns clj-puppetdb.core
  (:require [cheshire.core :as json]
            [clj-puppetdb.http :refer [GET] :as http]
            [clj-puppetdb.paging :as paging]
            [clj-puppetdb.query :as q]
            [clj-puppetdb.schema :refer [Client]]
            [schema.core :as s]))

(defn connect
  "Return a PuppetDB client map for the given host.

  If the host begins with 'https://', you must supply also supply a map containing
  a valid :ssl-ca-cert, :ssl-cert, and :ssl-key (as java.io.File objects).
 
  If the host begins with 'http://', that must be the only argument given.

  Either way, you must specify the port as part of the host URL (usually ':8080' for
  http or ':8081' for https)."
  ([^String host] (http/make-http-client host))
  ([^String host opts] (http/make-https-client host opts)))

(defn query
  "Use the given PuppetDB client to query the server.
  
  The path argument should be a valid endpoint, e.g. \"/v4/nodes\".

  The query-vec argument is optional, and should be a vector representing an API query,
  e.g. [:= [:fact \"operatingsystem\"] \"Linux\"]

  The `params` map is optional, and should contain the following keys:
  - :limit (the number of results to request)
  - :offset (optional: the index of the first result to return, default 0)
  - :order-by (a vector of maps, each specifying a :field and an :order key)
  For example: `{:limit 100 :offset 0 :order-by [{:field \"value\" :order \"asc\"}]}`"
  ([client path]
    (query client path nil nil))
  ([client path query-vec]
    (query client path query-vec nil))
  ([client path query-vec params]
    (let [merged-params (-> nil
                            (merge
                              (when query-vec
                                {:query (q/query->json query-vec)}))
                            (merge
                              (when params
                                (update-in params [:order-by] json/encode))))]
      (if merged-params
        (GET client path merged-params)
        (GET client path)))))

(defn lazy-query
  "Return a lazy sequence of results from the given query. Unlike the regular
  `query` function, `lazy-query` uses paging to fetch results gradually as they
  are consumed.

  The `params` map is required, and should contain the following keys:
  - :limit (the number of results to request)
  - :offset (optional: the index of the first result to return, default 0)
  - :order-by (a vector of maps, each specifying a :field and an :order key)
  For example: `{:limit 100 :offset 0 :order-by [{:field \"value\" :order \"asc\"}]}`"
  ([client path params]
    (paging/lazy-query client path params))
  ([client path query-vec params]
    (paging/lazy-query client path query-vec params)))
