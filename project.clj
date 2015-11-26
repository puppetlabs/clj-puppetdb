(defproject puppetlabs/clj-puppetdb "0.2.6-SNAPSHOT"
  :description "A Clojure client for the PuppetDB REST API"
  :url "https://github.com/puppetlabs/clj-puppetdb"
  :license {:name "Apache License, Version 2.0"
            :url  "http://www.apache.org/licenses/LICENSE-2.0.html"}
  ;; Abort when version ranges or version conflicts are detected in
  ;; dependencies. Also supports :warn to simply emit warnings.
  ;; requires lein 2.2.0+.
  :pedantic? :abort
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [cheshire "5.4.0"]
                 [com.cemerick/url "0.1.1"]
                 [me.raynes/fs "1.4.5"]
                 [puppetlabs/http-client "0.4.5"]
                 [prismatic/schema "0.4.0"]
                 [puppetlabs/kitchensink "1.0.0"]
                 [slingshot "0.12.2"]]
  :plugins [[lein-release "1.0.5"]]
  :lein-release {:scm        :git
                 :deploy-via :lein-deploy}
  :deploy-repositories [["releases" {:url           "https://clojars.org/repo"
                                     :username      :env/clojars_jenkins_username
                                     :password      :env/clojars_jenkins_password
                                     :sign-releases false}]]
  :repl-options {:init (do (require 'spyscope.core)
                           (use 'clj-puppetdb.testutils.repl))}
  :profiles {:dev {:dependencies [[org.clojure/tools.namespace "0.2.5"]
                                  [spyscope "0.1.5" :exclusions [clj-time]]]}})
