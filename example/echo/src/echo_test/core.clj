(ns echo-test.core 
  (:require [fdk-clj.core :as fdk])
  (:gen-class))

(defn handler [context data]
  (fdk/raw-response {
    :status 200
    :body "hello world"
    :content_type "text/plain"
    :headers { "X-Request-Is-Fancy" "Very" }
  }))

(defn -main [& args]
  (fdk/handle handler))