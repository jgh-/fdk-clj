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
       (is (= (timeout f {}) { :result { :raw-response { :status 408 }} :request {}}))
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
  (is (= (is-raw? { :raw-response { :hi "ok"}}) true))
  (is (= (is-raw? { :hi "ok"}) false)))

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
;; format-cloudevent
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
    r (format-cloudevent v)]
    (is (= r e)))))

;;
;;
;; format-json
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
    r (format-json v)]
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
    r (handle-request v handle-request-entrypoint-json)
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
    r (handle-request v handle-request-entrypoint-cloudevent)
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
    (let [r (handle-result (handle-request { :extensions { :deadline deadline } } handle-request-entrypoint-exception))
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
    (let [r (handle-result (handle-request { :deadline deadline } handle-request-empty-entrypoint))
          e { :content_type "application/json" 
              :body "{}"
              :protocol {
                :status_code 200
                :headers {}
                }}]
      (is (= r e)))))

(deftest handle-result-empty-ce
  (with-redefs [env env-cloudevent]
    (let [r (handle-result (handle-request { :extensions { :deadline deadline } } handle-request-empty-entrypoint))
          e { :contentType "application/json" 
              :data {}
              :extensions {
                :protocol {
                :status_code 200
                :headers {}
                }}}]
      (is (= r e)))))