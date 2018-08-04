(defproject echo-test "1.0.0"
  :description "Clojure FDK for Fn"
  :url "https://github.com/unpause-live/fdk-clj"
  :license {:name "Apache License Version 2.0"
        :url "http://www.apache.org/licenses/LICENSE-2.0.txt"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                [unpause/fdk-clj "1.0.2"]]
  :main echo-test.core
  :jvm-opts ["-Duser.timezone=UTC"]
  :profiles {:uberjar { :aot :all }})