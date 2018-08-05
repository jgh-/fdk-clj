(ns echo.core 
  (:require [fdk-clj.core :as fdk])
  (:gen-class))

(defn handler [context data]
  data)

(defn -main [& args]
  (fdk/handle handler))