(defproject clj-puppetdb "0.1.1"
  :description "A Clojure client for the PuppetDB REST API"
  :url "https://github.com/holguinj/clj-puppetdb"
  :license {:name "Apache License, Version 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [puppetlabs/http-client "0.2.0"]
                 [prismatic/schema "0.2.4"]
                 [com.cemerick/url "0.1.1"]
                 [cheshire "5.3.1"]])
