(defproject com.github.strojure/spin "0.1.0-SNAPSHOT"
  :description "Clojure HTTP server abstraction."
  :url "https://github.com/strojure/spin"
  :license {:name "The Unlicense" :url "https://unlicense.org"}

  :dependencies []

  :profiles {:provided {:dependencies [[org.clojure/clojure "1.11.1"]]}
             :dev {:source-paths ["examples/src"]
                   :dependencies [[io.undertow/undertow-core "2.3.12.Final"]
                                  [java-http-clj "0.4.3"]
                                  [org.eclipse.jetty/jetty-server "11.0.18"]]}})
