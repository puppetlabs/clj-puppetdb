(ns clj-puppetdb.http-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [clj-puppetdb.http :refer [GET make-client catching-exceptions assoc-kind]]
            [clj-puppetdb.http-core :refer :all]
            [cheshire.core :as json]
            [puppetlabs.http.client.async :as http])
  (:import [clojure.lang ExceptionInfo]))

(defn- test-query-params
  [client params assert-fn]
  (let [wrapped-client
        (reify
          PdbClient
          (pdb-get [this path params]
            (pdb-get this this path params))
          (pdb-get [_ that path params]
            (pdb-get client that path params))

          (pdb-do-get [_ query]
            (assert-fn query))

          (client-info [_]
            (client-info client)))]
    (pdb-get wrapped-client "" params)))

(deftest parameter-encoding-test
  (let [http-client (http/create-client {})]
    (let [client (make-client http-client "http://localhost:8080" {})]
      (testing "Should JSON encode parameters which requre it"
        (test-query-params client {:foo           [:bar "baz"]
                                   :counts_filter [:> "failures" 0]
                                   :query         [:= :certname "node"]
                                   :order_by      [{:field "status" :order "ASC"}]}
                           #(is (= % "http://localhost:8080?counts_filter=%5B%22%3E%22%2C%22failures%22%2C0%5D&foo=%5B%3Abar%20%22baz%22%5D&order_by=%5B%7B%22field%22%3A%22status%22%2C%22order%22%3A%22ASC%22%7D%5D&query=%5B%22%3D%22%2C%22certname%22%2C%22node%22%5D"))))
      (testing "Should leave already encoded params alone"
        (test-query-params client {:order_by "[{\"order\":\"ASC\",\"field\":\"status\"}]"}
                           #(is (= % "http://localhost:8080?order_by=%5B%7B%22order%22%3A%22ASC%22%2C%22field%22%3A%22status%22%7D%5D")))))
    (let [client (make-client http-client "http://localhost:8080" {:vcr-dir "foo"})]
      (testing "Should sort parameters contianing nested structures"
        (test-query-params client {:order_by [{:order "ASC" :field "status"}]}
                           #(is (= % "http://localhost:8080?order_by=%5B%7B%22field%22%3A%22status%22%2C%22order%22%3A%22ASC%22%7D%5D"))))
      (testing "Should sort parameters contianing nested structures even if already JSON encoded"
        (test-query-params client {:order_by "[{\"order\":\"ASC\",\"field\":\"status\"}]"}
                           #(is (= % "http://localhost:8080?order_by=%5B%7B%22field%22%3A%22status%22%2C%22order%22%3A%22ASC%22%7D%5D")))))))

(deftest GET-test
  (let [host "http://localhost:8080"
        path "/v4/nodes"
        params {:query [:= [:fact "operatingsystem"] "Linux"]}
        client (make-client (http/create-client {}) host {})
        response-data ["node-1" "node-2"]
        response-data-encoded (json/encode response-data)
        response-headers {"x-records" (.toString (count response-data))}
        fake-get (fn [status] {:status status :body (io/input-stream (.getBytes response-data-encoded)) :headers response-headers})]

    (testing "Should have proper response"
      (with-redefs [http/request-with-client (fn [_ _ _] (future (fake-get 200)))]
        (let [GET-response (GET client path params)]
          (is (= (first GET-response) response-data))
          (is (= (second GET-response) response-headers)))))

    (testing "Should throw proper exception"
      (with-redefs [http/request-with-client (fn [_ _ _] (future (fake-get 400)))]
        (try
          (GET client path params)
          (catch ExceptionInfo ei
            (let [info (.getData ei)]
              (is (= (:status info) 400))
              (is (= (:kind info) :puppetdb-query-error))
              (is (= (:params info) params))
              (is (= (:endpoint info) path))
              (is (= (:host info) host))
              (is (= (:msg info) response-data-encoded)))))))

    (testing "Should throw proper exception on an error response"
      (with-redefs [http/request-with-client (fn [_ _ _] (future ((constantly {:error "an exception"}))))]
        (try
          (GET client path params)
          (catch ExceptionInfo ei
            (let [info (.getData ei)]
              (is (= (:kind info) :puppetdb-connection-error))
              (is (= (:exception info) "an exception")))))))))

(deftest catching-exceptions-test
  (testing "Should pass"
    (is (= (catching-exceptions ((constantly {:body "foobar"})) (assoc-kind {} :something-bad-happened)) {:body "foobar"})))

  (testing "Should rethrow proper exception on an exception"
    (try
      (catching-exceptions (#(throw (NullPointerException.))) (assoc-kind {} :something-bad-happened))
      (is (not "Should never get to this place!!"))
      (catch ExceptionInfo ei
        (let [info (.getData ei)]
          (is (= (:kind info) :something-bad-happened))
          (is (instance? NullPointerException (:exception info)))))))

  (testing "Should rethrow proper exception on an exception that is listed"
    (try
      (catching-exceptions (#(throw (NullPointerException.))) (assoc-kind {} :something-bad-happened) NullPointerException)
      (is (not "Should never get to this place!!"))
      (catch ExceptionInfo ei
        (let [info (.getData ei)]
          (is (= (:kind info) :something-bad-happened))
          (is (instance? NullPointerException (:exception info)))))))

  (testing "Should throw original exception on an exception that is not listed"
    (try
      (catching-exceptions (#(throw (NullPointerException.))) (assoc-kind {} :something-bad-happened) ArithmeticException)
      (is (not "Should never get to this place!!"))
      (catch NullPointerException e
        (is (instance? NullPointerException e))))))