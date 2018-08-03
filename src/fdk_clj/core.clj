(ns fdk-clj.core 
  (:require [clojure.string :as s]
            [cheshire.core :refer :all]
            [clj-time.core :as t]
            [clj-time.format :as f])
  (:gen-class))


(declare handle-request)
(declare handle-result)

;
;
; Start runloop.  Call in your main function.  As an argument
; you must provide the function that is to be called when a new
; request comes in.
; That function must take a single argument which is a map with the following layout:
; {
;   :config { "FN_APP_NAME" "app" "FN_PATH" "func" ... env variables ... }
;   :content_type "application/json"
;   :deadline "2018-01-01T23:59:59.999"
;   :call_id "id"
;   :execution_type "sync"
;   :app "app"
;   :path "func"
;   :method "GET"
;   :headers { http headers}
;   :request_url "http://domain.com/r/app/func"
;   :data "body data"
;   [optional] :cloudevent { event map } ;; only applies to CloudEvent Format
; }
;
(defn handle [func-entrypoint]
  (let [rdr (clojure.java.io/reader *in*)]
    (doseq [line (line-seq rdr)]
      (let [inp (parse-string line true)
            res (handle-result (handle-request inp func-entrypoint))]
        (println (generate-string res))))))

;
;
;
;
;
;
;

(defonce env 
  {
    :app (System/getenv "FN_APP_NAME")
    :path (System/getenv "FN_PATH")
    :fmt (System/getenv "FN_FORMAT")
    :execution-type (System/getenv "FN_TYPE")
    :config (System/getenv)
  })


(defn handle-result [fn-res]
  (merge-with into {
    :content_type (if (string? (:body fn-res)) "text/plain" "application/json")
    :protocol {
        :status_code (:status fn-res)
    }
    :body (if (string? (:body fn-res)) (:body fn-res) (generate-string (:body fn-res) {:escape-non-ascii true}))
  } (if-not (nil? (:headers fn-res)) { :protocol { :headers (:headers fn-res) } })))

;
;
; See https://github.com/cloudevents/spec/blob/master/json-format.md
;
(defn format-cloudevent [req]
  {
    :call_id (-> req :eventID)
    :content_type (get req :contentType "application/cloudevents+json")
    :deadline (-> (req :extensions) :deadline)
    :execution_type (-> env :execution-type)
    :data (get req :data {})
    :cloudevent req
  })

(defn format-json [req]
  {
    :call_id (-> req :call_id)
    :content_type (get req :content_type "application/json")
    :deadline (-> req :deadline)
    :execution_type (get req :fn-type "sync")
    :data (get req :data {})
  })

(defn timeout [fx]
  (future-cancel fx)
  {
    :status 408
  })

(defn handle-request [req fn-entrypoint]
  (let [protocol (get req :protocol {
                          :headers {}
                          :type "http"
                          :method "GET"
                          :request_url (str "http://localhost:8080/" (:app env) "/" (:path env))
                        })
        request (merge {
      :config (:config env)
      :app (:app env)
      :path (:path env)
      :method (get protocol :method "GET")
      :headers (get protocol :headers {})
      :request_url (get protocol :request_url "")
    } (cond (= (:fmt env) "json") (format-json req)
            (= (:fmt env) "cloudevent") (format-cloudevent req)
            :else (throw (AssertionError. "'json' and 'cloudevent' are the only supported formats"))))]
    (let [fx (future (fn-entrypoint request))]
          (deref fx (t/in-millis (t/interval (t/now) (f/parse (-> request :deadline)))) (timeout fx)))))
