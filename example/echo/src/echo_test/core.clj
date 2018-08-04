(ns echo-test.core 
  (:require [fdk-clj.core :as fdk])
  (:gen-class))

(defn handler [context data]
  {
    :status 200
    :body body
    :content_type (:content_type context)
    :headers { "X-Request-Is-Fancy" "Very" }
  })

(defn -main [& args]
  (fdk/handle handler))