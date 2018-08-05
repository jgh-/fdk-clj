(defproject unpause/fdk-clj "1.0.4"
  :description "Clojure FDK for Fn"
  :url "https://github.com/unpause-live/fdk-clj"
  :license {:name "Apache License Version 2.0"
        :url "http://www.apache.org/licenses/LICENSE-2.0.txt"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                [cheshire "5.8.0"]
                [clj-time "0.14.4"]
                [org.clojure/test.check "0.9.0"]]
  :min-lein-version "2.0.0"
  :global-vars {*warn-on-reflection* true}
  :jvm-opts ["-Duser.timezone=UTC"]
  :profiles {:1.7 {:dependencies [[org.clojure/clojure "1.7.0"]]}
             :1.8 {:dependencies [[org.clojure/clojure "1.8.0"]]}
             :1.9 {:dependencies [[org.clojure/clojure "1.9.0"]]}
             :1.10 {:repositories [["snapshots" "https://oss.sonatype.org/content/repositories/snapshots/"]]
                    :dependencies [[org.clojure/clojure "1.10.0-master-SNAPSHOT"]]}
            }
  :test-paths ["test"]
  :aliases {"test-all" ["with-profile" "1.7,1.8,1.9,1.10" "test"]})