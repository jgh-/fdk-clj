(ns fdk-clj.core-test
  (:refer-clojure :exclude [extend second])
  (:require [clojure.test :refer :all]
            [clj-time.core :as t]
            [fdk-clj.core :refer :all]))


;;
;; timeout function
(deftest timeout-cancel
  (let [f (future (Thread/sleep 1000))
       v (deref f 100 :timeout)]
       (is (= v :timeout))
       (is (= (timeout f) { :status 408 }))
       (is (future-cancelled? f))))



(defonce test-env
  {
    :app "app"
    :path "test/test"
    :fmt "test"
    :execution-type "async"
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
        :call_id 1
        :content_type "text/plain"
        :deadline "1"
        :execution_type "async"
        :data "hi"
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
        :execution_type "async"
        :data "hi"
    }
    r (format-json v)]
    (is (= r e)))))

;;
;;
;;
;; handle-result (json)
(deftest handle-result-json
  (let [v {
    :body { :ok "ok" }
    :status 202
    :headers { :ok "ok" }
    }
    e {
      :content_type "application/json"
      :body "{\"ok\":\"ok\"}"
      :protocol {
        :status_code 202
        :headers { :ok "ok" }
      }
    }
    r (handle-result v)]
    (is (= r e))))

;;
;;
;;
;; handle result (plaintext)
(deftest handle-result-plain
  (let [v {
    :body "ok"
    :status 202
    :headers { :ok "ok" }
    }
    e {
      :content_type "text/plain"
      :body "ok"
      :protocol {
        :status_code 202
        :headers { :ok "ok" }
      }
    }
    r (handle-result v)]
    (is (= r e))))


;;
;;
;;
;; handle-request (json)
(defonce env-json
  {
    :app "app"
    :path "test/test"
    :fmt "json"
    :execution-type "sync"
    :config { :ok "ok" }
  })

(defonce deadline (t/plus (t/now) (t/minutes 10)))

(defn handle-request-entrypoint-json [req]
  (let [e {
        :config { :ok "ok" }
        :app "app"
        :path "test/test"
        :method "PUT"
        :headers { :ok "ok" }
        :request_url "http://test.com/r/app/test/test"
        :call_id 1
        :content_type "text/plain"
        :deadline deadline
        :execution_type "sync"
        :data "hi"
    }]
    (is (= e req))
    {
        :status 202
        :body { :ok "ok" }
        :headers { :h "h" }
    }))

(deftest handle-request-json
  (with-redefs [env env-json]
    (let [v {
        :protocol {
            :method "PUT"
            :headers { :ok "ok" }
            :request_url "http://test.com/r/app/test/test"
        }
        :call_id 1
        :content_type "text/plain"
        :deadline deadline
        :fn-type "sync"
        :data "hi"
    }
    r (handle-request v handle-request-entrypoint-json)
    e {
      :status 202
      :body { :ok "ok" }
      :headers { :h "h" }
      }]
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
    :execution-type "sync"
    :config { :ok "ok" }
  })

(defn handle-request-entrypoint-cloudevent [req]
  (let [e {
        :config { :ok "ok" }
        :app "app"
        :path "test/test"
        :method "PUT"
        :headers { :ok "ok" }
        :request_url "http://test.com/r/app/test/test"
        :call_id 1
        :content_type "text/plain"
        :deadline deadline
        :execution_type "sync"
        :data "hi"
        :cloudevent {
            :protocol {
                :method "PUT"
                :headers { :ok "ok" }
                :request_url "http://test.com/r/app/test/test"
            }
            :eventID 1
            :contentType "text/plain"
            :extensions {:deadline deadline }
            :data "hi"
        }
    }]
    (is (= e req))
    {
        :status 202
        :body { :ok "ok" }
        :headers { :h "h" }
    }))

(deftest handle-request-cloudevent
  (with-redefs [env env-cloudevent]
    (let [v {
        :protocol {
            :method "PUT"
            :headers { :ok "ok" }
            :request_url "http://test.com/r/app/test/test"
        }
        :eventID 1
        :contentType "text/plain"
        :extensions {:deadline deadline }
        :data "hi"
    }
    r (handle-request v handle-request-entrypoint-cloudevent)
    e {
      :status 202
      :body { :ok "ok" }
      :headers { :h "h" }
      }]
    (is (= r e)))))
