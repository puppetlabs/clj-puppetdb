(ns clj-puppetdb.core-test
  (:require [clojure.test :refer :all]
            [clj-puppetdb.core :refer :all]
            [clj-puppetdb.http-core :refer :all]
            [clj-puppetdb.http :refer [GET]]
            [puppetlabs.http.client.sync :as http]
            [puppetlabs.http.client.async :as async]))

(deftest connect-test
  (let [http-client (async/create-client {})]
    (testing "Should create a connection with http://localhost:8080 with no additional arguments"
      (let [conn (connect http-client "http://localhost:8080")]
        (is (= (client-info conn) {:host "http://localhost:8080"}))))
    (testing "Should create a connection with http://localhost:8080 and VCR enabled"
      (let [conn (connect http-client "http://localhost:8080" {:vcr-dir "/temp"})]
        (is (= (client-info conn) {:host "http://localhost:8080" :vcr-dir "/temp"}))))
    (testing "Should accept https://puppetdb:8081 with no opts"
      (let [conn (connect http-client "https://puppetdb:8081" {})]
        ;; I'm only testing for truthiness of conn here. Schema validation should handle the rest,
        ;; and testing equality with java.io.File objects doesn't seem to work.
        (is conn)))
    (testing "Should accept VCR enabled"
      (let [opts {:vcr-dir "/temp"}
            conn (connect http-client "https://puppetdb:8081" opts)]
        ;; I'm testing for truthiness of conn here. Schema validation should handle the rest except the VCR piece,
        ;; and testing equality with java.io.File objects doesn't seem to work.
        (is conn)
        (is (= (:vcr-dir opts) (:vcr-dir (client-info conn))))))
    (testing "Should not accept bad host"
      (is (thrown? AssertionError (connect http-client "httpXXX://puppetdb:8081" {}))))
    (testing "Should do proper GET request"
      (let [host "http://localhost:8080"
            path "/v4/nodes"
            params {:query [:= [:fact "operatingsystem"] "Linux"]}
            url-params "?query=%5B%22%3D%22%2C%5B%22fact%22%2C%22operatingsystem%22%5D%2C%22Linux%22%5D"
            conn (connect http-client host)]
        (with-redefs [http/get (fn [query _] (is (= query (str host path url-params))) [])]
          (pdb-get conn path params)
          (pdb-do-get conn (str host path url-params)))))))

(deftest query-test
  (let [data ["node-01" "node-02" "node-03" "node-04"]
        metadata {:total (count data)}
        client "a client"
        path "/v4/nodes"]
    (testing "Should return data when parameters are not used"
      (with-redefs [GET (fn[_ _ _]
                          [data {"x-records" (.toString (count data))}])]
        (is (= (query client path)
               data))
        (is (= (query-with-metadata client path {})
               [data metadata]))))
    (testing "Should return data when parameters are used"
      (let [query-vec [:= [:fact "operatingsystem"] "Linux"]]
        (with-redefs [GET (fn[_ _ params]
                            (is (= (:query params) query-vec))
                            [data {"x-records" (.toString (count data))}])]
          (is (= (query client path query-vec)
                 data))
          (is (= (query-with-metadata client path query-vec {})
                 [data metadata]))
          (is (= (query-with-metadata client path {:query query-vec})
                 [data metadata])))))))

(deftest lazy-query-test
  (testing "Should automatically fetch three pages by two items"
    (let [data [["A" "B"] ["C" "D"] ["E" "F"]]]
      (with-redefs [clj-puppetdb.http/GET (fn[_ _ {:keys [offset]}]
                                            (let [idx (/ offset 2)]
                                              (when (< idx 3) (nth data idx))))]
        (let [seq (lazy-query nil nil {:limit 2 :order-by nil})]
          (is (= seq (flatten data)))))))
  (testing "Should automatically fetch two pages by tree items"
    (let [data [["A" "B" "C"] [ "D" "E" "F"]]]
      (with-redefs [clj-puppetdb.http/GET (fn[_ _ {:keys [offset]}]
                                            (let [idx (/ offset 3)]
                                              (when (< idx 2) (nth data idx))))]
        (let [seq (lazy-query nil nil {:limit 3 :order-by nil})]
          (is (= seq (flatten data))))))))
