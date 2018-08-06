(ns fdk-clj.core-test
  (:refer-clojure :exclude [extend second])
  (:require [clojure.test :refer :all]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [fdk-clj.core :refer :all]))


;;
;; timeout function
(deftest timeout-cancel
  (let [f (future (Thread/sleep 1000))
       v (deref f 100 :timeout)]
       (is (= v :timeout))
       (is (= (timeout f) { :raw-response { :status 504 } }))
       (is (future-cancelled? f))))


(deftest gofmt-headers-test
  (is (= (gofmt-headers { :h1 "h1"}) { :h1 ["h1"]}))
  (is (= (gofmt-headers { :h1 ["h1"]}) { :h1 ["h1"]}))
  (is (= (gofmt-headers { :h1 "h1" :h2 "h2"}) { :h1 ["h1"] :h2 ["h2"] }))
  (is (= (gofmt-headers { :h1 ["h1"] :h2 ["h2"]}) { :h1 ["h1"] :h2 ["h2"] }))
  (is (= (gofmt-headers { "X-Different-Header" "h1" }) { "X-Different-Header" ["h1"]})))

(deftest raw-response-test
  (is (= {:raw-response { :hi 2 } } (raw-response { :hi 2 }))))

(deftest israw-test
  (is (= (raw? { :raw-response { :hi "ok"}}) true))
  (is (= (raw? { :hi "ok"}) false)))

(defonce test-env
  {
    :app "app"
    :path "test/test"
    :fmt "json"
    :type "sync"
    :config { :ok "ok" }
  })

;;
;;
;; request-cloudevent
(deftest fmt-cloudevent
  (with-redefs [env test-env]
    (let [v {
        :eventID 1
        :contentType "text/plain"
        :extensions { :deadline "1" }
        :data "hi"
    }
    e {
        :request_url "http://localhost:8080/r/app/test/test" 
        :headers {}
        :call_id 1
        :content_type "text/plain"
        :deadline "1"
        :cloudevent v
    }
    r (request-cloudevent v)]
    (is (= r e)))))

;;
;;
;; request-json
(deftest fmt-json
  (with-redefs [env test-env]
    (let [v {
        :call_id 1
        :content_type "text/plain"
        :deadline "1"
        :fn-type "async"
        :data "hi"
    }
    e {
        :call_id 1
        :content_type "text/plain"
        :deadline "1"
        :headers {}
        :request_url "http://localhost:8080/r/app/test/test"
    }
    r (request-json v)]
    (is (= r e)))))

;;
;;
;;
;; handle-result (json)
(deftest handle-result-json
  (with-redefs [env test-env]
    (let [v { :result (raw-response {
      :body { :ok "ok" }
      :status 202
      :headers { :ok ["ok"] }
      }) }
      e {
        :content_type "application/json"
        :body "{\"ok\":\"ok\"}"
        :protocol {
          :status_code 202
          :headers { :ok ["ok"] }
        }
      }
      r (handle-result v)]
      (is (= r e)))))

;;
;;
;;
;; handle result (plaintext)
(deftest handle-result-plain
  (with-redefs [env test-env]
    (let [v { :result (raw-response {
      :body "ok"
      :status 202
      :headers { 
        :ok ["ok"] 
        :content-type "text/plain"}
      }) }
      e {
        :content_type "text/plain"
        :body "ok"
        :protocol {
          :status_code 202
          :headers { :ok ["ok"] :content-type ["text/plain"] }
        }
      }
      r (handle-result v)]
      (is (= r e)))))


;;
;;
;;
;; handle-request (json)
(defonce env-json
  {
    :app "app"
    :path "test/test"
    :fmt "json"
    :type "sync"
    :config { :ok "ok" }
  })

(defonce deadline (f/unparse (f/formatters :date-time) (t/plus (t/now) (t/minutes 10))))

(defn handle-request-entrypoint-json [ctx body]
  (let [e {
        :config { :ok "ok" }
        :app_name "app"
        :app_route "test/test"
        :fn_format "json"
        :execution_type "sync"
        :arguments {}
        :headers { :ok ["ok"] }
        :request_url "http://test.com/r/app/test/test"
        :call_id 1
        :content_type "text/plain"
        :deadline deadline
    }]
    (is (= e ctx))
    (is (= "hi" body))
    (raw-response {
        :content_type "text/plain"
        :status 202
        :body "ok"
        :headers { :h "h" }
    })))

(deftest handle-request-json
  (with-redefs [env env-json]
    (let [v {
        :protocol {
            :method "PUT"
            :headers { :ok ["ok"] }
            :request_url "http://test.com/r/app/test/test"
        }
        :call_id 1
        :content_type "text/plain"
        :deadline deadline
        :fn-type "sync"
        :data "hi"
    }
    r (call-handler handle-request-entrypoint-json v (handle-request v))
    e { :result { :raw-response {
      :status 202
      :body "ok"
      :content_type "text/plain"
      :headers { :h "h" }
      }} :request v }]
    (is (= r e)))))

;;
;;
;;
;; handle-request (cloudevent)
(defonce env-cloudevent
  {
    :app "app"
    :path "test/test"
    :fmt "cloudevent"
    :type "sync"
    :config { :ok "ok" }
  })

(defn handle-request-entrypoint-cloudevent [ctx body]
  (let [e {
        :config { :ok "ok" }
        :app_name "app"
        :app_route "test/test"
        :fn_format "cloudevent"
        :execution_type "sync"
        :arguments {}
        :headers { :ok ["ok"] }
        :request_url "http://test.com/r/app/test/test"
        :call_id 1
        :content_type "text/plain"
        :deadline deadline
        :cloudevent {
            :eventID 1
            :contentType "text/plain"
            :extensions {
              :deadline deadline 
              :protocol {
                :method "PUT"
                :headers { :ok ["ok"] }
                :request_url "http://test.com/r/app/test/test"
            }}
            :data "hi"
        }
    }]
    (is (= e ctx))
    (is (= "hi" body))
    (raw-response {
        :status 202
        :body { :ok "ok" }
        :headers { :h "h" }
    })))

(deftest handle-request-cloudevent
  (with-redefs [env env-cloudevent]
    (let [v {
        :eventID 1
        :contentType "text/plain"
        :extensions { 
          :deadline deadline
          :protocol {
                :method "PUT"
                :headers { :ok ["ok"] }
                :request_url "http://test.com/r/app/test/test"
          }
        }
        :data "hi"
    }
    r (call-handler handle-request-entrypoint-cloudevent v (handle-request v))
    e { :result { :raw-response {
      
      :status 202
      :body { :ok "ok" }
      :headers { :h "h" }
      } } :request v }]
    (is (= r e)))))

;;
;;
;;
;; handle-result (json, using cloudevent)
(deftest handle-result-json-ce
  (with-redefs [env env-cloudevent]
    (let [v { :result (raw-response {
      :body { :ok "ok" }
      :status 202
      :headers { :ok "ok" }
      }) :request {
          :cloudevent {
            :eventID 1
            :contentType "text/plain"
            :extensions {
              :deadline deadline 
              :protocol {
                :method "PUT"
                :headers { :ok ["ok"] }
                :request_url "http://test.com/r/app/test/test"
            }}
            :data "hi"
        }
      }}
      e {
        :eventID 1
        :contentType "application/json"
        :data { :ok "ok" }
        :extensions {
          :protocol {
            :status_code 202
            :headers { :ok ["ok"] }
          }
        }
      }
      r (handle-result v)]
      (is (= r e)))))


;;
;;
;;
;; handle result exception
(defn handle-request-entrypoint-exception [ctx body]
  (/ 1 0))

(deftest handle-result-exception
  (with-redefs [env env-cloudevent]
    (let [req { :extensions { :deadline deadline } }
          r (handle-result (call-handler handle-request-entrypoint-exception req (handle-request req)))
          e { :contentType "application/json" 
              :data {}
              :extensions {
                :protocol {
                  :status_code 500
                  :headers {}
                  }}}]
      (is (= r e)))))


;;
;;
;;
;; handle result, empty return from handler function
(defn handle-request-empty-entrypoint [ctx body])

(deftest handle-result-empty-json
  (with-redefs [env env-json]
    (let [req { :deadline deadline }
          r (handle-result (call-handler handle-request-empty-entrypoint req (handle-request req)))
          e { :content_type "application/json" 
              :body "{}"
              :protocol {
                :status_code 200
                :headers {}
                }}]
      (is (= r e)))))

(deftest handle-result-empty-ce
  (with-redefs [env env-cloudevent]
    (let [req { :extensions { :deadline deadline } }
          r (handle-result (call-handler handle-request-empty-entrypoint req (handle-request req)))
          e { :contentType "application/json" 
              :data {}
              :extensions {
                :protocol {
                :status_code 200
                :headers {}
                }}}]
      (is (= r e)))))

;;
;;
;;
;; handle result, convenient return from handler function
(defn handle-request-convenient-entrypoint [ctx body] body)

(deftest handle-result-convenient-json
  (with-redefs [env env-json]
    (let [req { :data { :hi 1 } :deadline deadline }
          r (handle-result (call-handler handle-request-convenient-entrypoint req (handle-request req)))
          e { :content_type "application/json" 
              :body "{\"hi\":1}"
              :protocol {
                :status_code 200
                :headers {}
                }}]
      (is (= r e)))))

(deftest handle-result-convenient-ce
  (with-redefs [env env-cloudevent]
    (let [req { :data { :hi 1 } :extensions { :deadline deadline } }
          r (handle-result (call-handler handle-request-convenient-entrypoint req (handle-request req)))
          e { :contentType "application/json" 
              :data { :hi 1 }
              :extensions {
                :protocol {
                :status_code 200
                :headers {}
                }}}]
      (is (= r e)))))

;;
;;
;;
;; call-handler with cancel
(defn call-handler-timeout-handler [ctx body] (Thread/sleep 1000))

(deftest call-handler-timeout
  (let [r (call-handler call-handler-timeout-handler 
                        {} { :deadline (f/unparse (f/formatters :date-time) (t/plus (t/now) (t/millis 100))) })]
  (is (= r { :result { :raw-response { :status 504 } } :request {} }))))

;;
;;
;;
;; handle result with text/plain and nil body

(deftest handle-result-textplain-nil 
  (with-redefs [env env-cloudevent] 
    (let [r (handle-result {:result { :raw-response { :headers { :content-type "text/plain" } } } })]
      (is (= r { :contentType "text/plain" :data {} :extensions { :protocol { :status_code 200 :headers { :content-type ["text/plain"]}}}}))))
  (with-redefs [env env-json]
    (let [r (handle-result {:result { :raw-response { :headers { :content-type "text/plain" } } } })]
      (is (= r { :content_type "text/plain" :body ""  :protocol { :status_code 200 :headers { :content-type ["text/plain"]}}})))))
