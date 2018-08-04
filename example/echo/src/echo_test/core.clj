(ns echo-test.core 
  (:require [fdk-clj.core :as fdk])
  (:gen-class))

(defn handler [context data]
  (binding [*out* *err*]
    (println (str "context: " context))
    (println (str "data: " data)))
  data)

(defn -main [& args]
  (fdk/handle handler))