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
    :config (System/getenv)
  })

(defn gofmt-headers [headers]
  (reduce-kv (fn [a k v] (conj a { k (into [] (flatten [v])) })) {} headers))

(defn result-cloudevent [ctx]
  (merge (:cloudevent (:request ctx)) {
    :contentType (if (string? (:body (:result ctx))) "text/plain" "application/json")
    :data (get (:result ctx) :body {})
    :extensions {
      :protocol {
        :status_code (:status (:result ctx))
        :headers (gofmt-headers (-> (ctx :result) :headers))
      }}}))

(defn result-json [ctx]
  (let [body (:body (:result ctx))]
  {
    :body (cond (nil? body) "" (string? body) body :else (generate-string body {:escape-non-ascii true}))
    :content_type (if (string? (:body (:result ctx))) "text/plain" "application/json")
    :protocol {
      :status_code (:status (:result ctx))
      :headers (gofmt-headers (-> (ctx :result) :headers))
    }
  }))

(defn handle-result [ctx]
    (if (= (:fmt env) "cloudevent") (result-cloudevent ctx) (result-json ctx)))

;
;
; See https://github.com/cloudevents/spec/blob/master/json-format.md
;
(defn format-cloudevent [req]
  {
    :call_id (-> req :eventID)
    :content_type (get req :contentType "application/cloudevents+json")
    :deadline (-> (req :extensions) :deadline)
    :data (get req :data {})
    :cloudevent req
    :method (get (-> (req :extensions) :protocol) :method "GET")
    :headers (get (-> (req :extensions) :protocol) :headers {})
    :request_url (get (-> (req :extensions) :protocol) :request_url (str "http://localhost:8080/r/" (:app env) "/" (:path env)))
  })

(defn format-json [req]
  {
    :call_id (-> req :call_id)
    :content_type (get req :content_type "application/json")
    :deadline (-> req :deadline)
    :data (get req :data {})
    :method (get (-> req :protocol) :method "GET")
    :headers (get (-> req :protocol) :headers {})
    :request_url (get (-> req :protocol) :request_url (str "http://localhost:8080/r/" (:app env) "/" (:path env)))
  })

(defn timeout [fx req]
  (future-cancel fx)
  { :result {
    :status 408
  } :request req })

(defn handle-request [req fn-entrypoint]
  (try 
    (let [request (merge {
            :config (:config env)
            :app (:app env)
            :path (:path env)
          }
          (cond (= (:fmt env) "json") (format-json req)
                (= (:fmt env) "cloudevent") (format-cloudevent req)
                :else (throw (AssertionError. "'json' and 'cloudevent' are the only supported formats"))))
        fx (future (fn-entrypoint request))]
            { :result (deref fx (t/in-millis (t/interval (t/now) (f/parse (-> request :deadline)))) (timeout fx req)) 
            :request req })
    (catch Exception e {:result { :status 500 } :request req}))
  )
