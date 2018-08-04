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
;   :app_name "app"
;   :app_route "/func"
;   :call_id "id"
;   :config { "FN_APP_NAME" "app" "FN_PATH" "func" ... env variables ... }
;   :headers { http headers }
;   :arguments {}
;   :fn_format "cloudevent"
;   :execution_type "sync"
;   :deadline "2018-01-01T23:59:59.999"
;   :content_type "application/json"
;   :request_url "http://domain.com/r/app/func"
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
    :type (System/getenv "FN_TYPE")
    :config (System/getenv)
  })

(defn gofmt-headers [headers]
  (reduce-kv (fn [a k v] (conj a { k (into [] (flatten [v])) })) {} headers))

(defn result-cloudevent [ctx]
  (merge (:cloudevent (:request ctx)) {
    :contentType (get (:result ctx) :content_type "application/json")
    :data (get (:result ctx) :body {})
    :extensions {
      :protocol {
        :status_code (get (:result ctx) :status 200)
        :headers (gofmt-headers (-> (ctx :result) :headers))
      }}}))

(defn result-json [ctx]
  (let [body (:body (:result ctx))
        content-type (:content_type (:result ctx))]
  {
    :body (if (or (nil? content-type) (= content-type "application/json")) (generate-string body {:escape-non-ascii true}) body)
    :content_type (get (:result ctx) :content_type "application/json")
    :protocol {
      :status_code (get (:result ctx) :status 200)
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
    :cloudevent req
    :deadline (:deadline (req :extensions))
    :headers (get (-> (req :extensions) :protocol) :headers {})
    :request_url (get (-> (req :extensions) :protocol) :request_url (str "http://localhost:8080/r/" (:app env) "/" (:path env)))
  })

(defn format-json [req]
  {
    :call_id (-> req :call_id)
    :content_type (get req :content_type "application/json")
    :deadline (:deadline req)
    :headers (get (-> req :protocol) :headers {})
    :request_url (get (-> req :protocol) :request_url (str "http://localhost:8080/r/" (:app env) "/" (:path env)))
  })

(defn timeout [fx req]
  (future-cancel fx)
  { :result {
    :status 408
  } :request req })

(defn handle-request [req fn-entrypoint]
  (let [ctx (merge {
          :app_name (:app env)
          :app_route (:path env)
          :config (:config env)
          :fn_format (:fmt env)
          :execution_type (:type env)
          :arguments {}
        }
        (cond (= (:fmt env) "json") (format-json req)
              (= (:fmt env) "cloudevent") (format-cloudevent req)
              :else (throw (AssertionError. "'json' and 'cloudevent' are the only supported formats"))))
      ms (t/in-millis (t/interval (t/now) (f/parse (:deadline ctx))))
      fx (future (try (fn-entrypoint ctx (:data req)) (catch Exception e :exception)))
      res (deref fx ms :timeout)]
          (if (= res :exception) { :result { :status 500 } :request req } { :result  (if (= res :timeout) (timeout fx req) res) :request req })))
