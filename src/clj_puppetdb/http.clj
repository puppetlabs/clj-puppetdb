(ns clj-puppetdb.http
  (:require [cemerick.url :refer [map->query]]
            [cheshire.core :as json]
            [clj-puppetdb.http-core :refer :all]
            [clj-puppetdb.query :as q]
            [clj-puppetdb.vcr :refer [make-vcr-client]]
            [clojure.tools.logging :as log]
            [me.raynes.fs :as fs]
            [puppetlabs.http.client.async :as http-async]
            [puppetlabs.http.client.common :as http-common]
            [puppetlabs.ssl-utils.core :as ssl]
            [schema.core :as s]
            [slingshot.slingshot :refer [throw+]])
  (:import [java.io IOException File]
           [javax.net.ssl SSLContext]
           [com.fasterxml.jackson.core JsonParseException]))

;; TODO:
;;   - Validate schema for GET params. The GetParams schema
;;     exists, but needs work before it can be used.

(def cert-keys
  "The keys to the configuration map specifying the certificates/private key
  needed for creating the SSL context.
  Warning: the order of the keys must match that expected by the
  `puppetlabs.ssl-utils.core/pems->ssl-context` function."
  [:ssl-cert :ssl-key :ssl-ca-cert])

(def connection-relevant-opts
  [:ssl-context :connect-timeout-milliseconds :socket-timeout-milliseconds])

(defn- make-client-common
  [http-async-client ^String host opts]
  (let [conn-opts (select-keys opts connection-relevant-opts)
        req-opts (apply dissoc opts connection-relevant-opts)]
    (reify
      PdbClient
      (pdb-get [this path params]
        (pdb-get this this path params))
      ; this arity is meant to support a kind of polymorphism
      (pdb-get [_ that path params]
        (let [query (if (empty? params)
                      (str host path)
                      (str host path \? (-> params
                                            q/params->json
                                            map->query)))]
          (pdb-do-get that query)))

      (pdb-do-get [_ query]
        (log/debug (str "GET:" query))
        @(http-common/get http-async-client query req-opts))

      (client-info [_]
        (assoc conn-opts :host host)))))

(defn- file?
  [^String file-path]
  (if (nil? file-path)
    nil
    (-> file-path
        File.
        fs/file?)))

(defn make-client
  [http-async-client ^String host opts]
  {:pre  [(or (.startsWith host "https://")
              (.startsWith host "http://"))]
   :post [(satisfies? PdbClient %)]}
  (let [vcr-dir (:vcr-dir opts)
        opts (dissoc opts :vcr-dir)
        client (make-client-common http-async-client host opts)]
    (if vcr-dir
      (make-vcr-client vcr-dir client)
      client)))

(defmacro assoc-kind
  "Associate the supplied `kind` value with the :kind key in the given `exception-structure` map."
  [exception-structure kind]
  `(assoc ~exception-structure :kind ~kind))

(defmacro catching-exceptions
  "Execute the `call` in a try-catch block, catching the named `exceptions` (or any subclasses
  of `java.lang.Throwable`) and rethrowing them as :exception in the `exception-structure`."
  [call exception-structure & exceptions]
  (let [exceptions (if (empty? exceptions) [Throwable] exceptions)]
    `(try
       ~call
       ~@(map (fn [exception]
                `(catch ~exception exception#
                   (throw+ (assoc ~exception-structure :exception exception#))))
              exceptions))))

(defmacro catching-parse-exceptions
  "A convenience macro for wrapping JSON parsing code. It simply delegates to the
  `catching-exceptions` macro supplying arguments to it suitable for the JSON parsing."
  [call exception-structure]
  `(catching-exceptions
     ~call
     (assoc-kind ~exception-structure :puppetdb-parse-error) JsonParseException IOException))

(defn- lazy-seq-catching-parse-exceptions
  "Given a lazy sequence wrap it into another lazy sequence which ensures that proper error
  handling is in place whenever an element is consumed from the sequence."
  [result exception-structure]
  (lazy-seq
    (if-let [sequence (catching-parse-exceptions (seq result) exception-structure)]
      (cons (first sequence) (lazy-seq-catching-parse-exceptions (rest sequence) exception-structure))
      result)))

(defn- decode-stream-catching-parse-exceptions
  "JSON decode data from given reader making sure proper error handling is in place."
  [reader exception-structure]
  (let [result (catching-parse-exceptions (json/decode-stream reader keyword) exception-structure)]
    (if (seq? result)
      (lazy-seq-catching-parse-exceptions result exception-structure)
      result)))

(s/defn ^:always-validate GET
  "Make a GET request using the given PuppetDB client, returning the results
  as a clojure data structure. If the structure contains any maps then keys
  in those maps will be keywordized.

  The `path` argument must be a URL-encoded string.

  You may provide a set of querystring parameters as a map. These will be url-encoded
  automatically and added to the path."
  ([client path params]
    {:pre [(satisfies? PdbClient client) (instance? String path) (map? params)]}
    (let [query-info (-> (client-info client)
                         (assoc :endpoint path)
                         (assoc :params params))
          connection-error-structure (assoc-kind query-info :puppetdb-connection-error)
          response (-> (pdb-get client path params)
                       (catching-exceptions connection-error-structure))]

      (when-let [exception (:error response)]
        (throw+ (assoc connection-error-structure :exception exception)))

      (if-not (= 200 (:status response))
        (throw+ (-> query-info
                                (assoc-kind :puppetdb-query-error)
                                (assoc :status (:status response))
                                (assoc :msg (slurp (make-response-reader response))))))
      (let [data (-> response
                     make-response-reader
                     (decode-stream-catching-parse-exceptions query-info))
            headers (:headers response)]
        [data headers])))

  ([client path]
    (GET client path {})))
