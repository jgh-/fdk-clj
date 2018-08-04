(ns echo-test.core 
  (:require [fdk-clj.core :as fdk])
  (:gen-class))

(defn func [request]
  {
    :status 200
    :body { :echo request }
    :headers { "X-Request-Is-Fancy" "Very" }
  })

(defn -main [& args]
  (fdk/handle func))