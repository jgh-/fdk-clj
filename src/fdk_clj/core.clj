(ns fdk-clj.core 
  (:require [clojure.string :as s]
            [cheshire.core :refer :all]
            [clj-time.core :as t]
            [clj-time.format :as f])
  (:gen-class))


(declare handle-request)
(declare handle-result)
(declare call-handler)
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
(defn handle [handler]
  (let [rdr (clojure.java.io/reader *in*)]
    (doseq [line (line-seq rdr)]
      (if-let [request (parse-string line true)]
        (let [res (handle-result (call-handler handler request (handle-request request)))]
          (println (generate-string res)))))))

(defn raw-response [response]
  { :raw-response response })

;
;
;
;
; "Private" functionality
;

(defonce env 
  {
    :app (System/getenv "FN_APP_NAME")
    :path (System/getenv "FN_PATH")
    :fmt (System/getenv "FN_FORMAT")
    :type (System/getenv "FN_TYPE")
    :config (System/getenv)
  })

(defn raw? [result]
  (not (nil? (:raw-response result))))

;;
;;
;; Handler functionality
;;

; Cancel a future and return 504 error
(defn timeout [fut]
  (future-cancel fut)
  (raw-response { :status 504 }))

; Call the developer-provided handler.  If it throws an uncaught exception, return 500. If it runs past the deadline
; cancel it and throw a 504 Timed Out.
(defn call-handler [handler req context]
  (let [ms (t/in-millis (t/interval (t/now) (f/parse (:deadline context))))
        fut (future (try (handler context (:data req)) (catch Exception e :exception)))
        res (deref fut ms :timeout)]
    { :request req 
      :result (cond (= res :exception) (raw-response { :status 500 })
                    (= res :timeout) (timeout fut)
                    :else (if (raw? res) res (raw-response (if (nil? res) {} { :body res }))))
    }))

;;
;; Request Handling
;;
;;

; Parse CloudEvent and emit map to be merged into Context
; See https://github.com/cloudevents/spec/blob/master/json-format.md
(defn request-cloudevent [req]
  {
    :call_id (get req :eventID "0")
    :content_type (get req :contentType "application/cloudevents+json")
    :cloudevent req
    :deadline (get (:extensions req) :deadline (f/unparse (f/formatters :date-time) (t/plus (t/now) (t/minutes 1))))
    :headers (get (-> (req :extensions) :protocol) :headers {})
    :request_url (get (-> (req :extensions) :protocol) :request_url (str "http://localhost:8080/r/" (:app env) "/" (:path env)))
  })

; Parse JSON format and emit map to be merged into Context
(defn request-json [req]
  {
    :call_id (get req :call_id "0")
    :content_type (get req :content_type "application/json")
    :deadline (get req :deadline (f/unparse (f/formatters :date-time) (t/plus (t/now) (t/minutes 1))))
    :headers (get (-> req :protocol) :headers {})
    :request_url (get (-> req :protocol) :request_url (str "http://localhost:8080/r/" (:app env) "/" (:path env)))
  })

; Merge environment variables into Context with incoming request (either JSON or CloudEvents format)
(defn handle-request [req]
  (merge {
      :app_name (:app env)
      :app_route (:path env)
      :config (:config env)
      :fn_format (:fmt env)
      :execution_type (:type env)
      :arguments {}
    }
    (cond (= (:fmt env) "json") (request-json req)
          (= (:fmt env) "cloudevent") (request-cloudevent req)
          :else (throw (AssertionError. "'json' and 'cloudevent' are the only supported formats")))))

;;
;;  Response Handling
;; 
;;
;;

; For Go the headers need to be in the form 
; { "header1": ["value1"] }
; So in this function we convert the { :key "value" } form into { :key ["value"] }
(defn gofmt-headers [headers]
  (reduce-kv (fn [a k v] (conj a { k (into [] (flatten [v])) })) {} headers))

;
; Format the result into a CloudEvents response
(defn result-cloudevent [ctx]
  (let [result (:raw-response (:result ctx))]
    (merge (:cloudevent (:request ctx)) {
      :contentType (get (:headers result) :content-type "application/json")
      :data (get result :body {})
      :extensions {
        :protocol {
          :status_code (get result :status 200)
          :headers (gofmt-headers (get result :headers {}))
        }}})))

;
; Format the result into a JSON response
(defn result-json [ctx]
  (let [result (:raw-response (:result ctx))
        content-type (get (:headers result) :content-type "application/json")]
  {
    :body (if (= content-type "application/json") (generate-string (get result :body {}) {:escape-non-ascii true}) 
                                                  (get result :body ""))
    :content_type content-type
    :protocol {
      :status_code (get result :status 200)
      :headers (gofmt-headers (get result :headers {}))
    }
  }))

; Handle the result from the developer's Function and passes it for either JSON or CloudEvents formatting
(defn handle-result [ctx]
    (if (= (:fmt env) "cloudevent") (result-cloudevent ctx) (result-json ctx)))

