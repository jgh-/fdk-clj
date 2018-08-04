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
       (is (= (timeout f {}) { :result { :status 408 } :request {}}))
       (is (future-cancelled? f))))


(deftest gofmt-headers-test
  (is (= (gofmt-headers { :h1 "h1"}) { :h1 ["h1"]}))
  (is (= (gofmt-headers { :h1 ["h1"]}) { :h1 ["h1"]}))
  (is (= (gofmt-headers { :h1 "h1" :h2 "h2"}) { :h1 ["h1"] :h2 ["h2"] }))
  (is (= (gofmt-headers { :h1 ["h1"] :h2 ["h2"]}) { :h1 ["h1"] :h2 ["h2"] }))
  (is (= (gofmt-headers { "X-Different-Header" "h1" }) { "X-Different-Header" ["h1"]})))

(defonce test-env
  {
    :app "app"
    :path "test/test"
    :fmt "json"
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
        :method "GET"
        :request_url "http://localhost:8080/r/app/test/test" 
        :headers {}
        :call_id 1
        :content_type "text/plain"
        :deadline "1"
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
        :data "hi"
        :method "GET" 
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
    (let [v { :result {
      :body { :ok "ok" }
      :status 202
      :headers { :ok ["ok"] }
      } }
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
    (let [v { :result {
      :body "ok"
      :status 202
      :headers { :ok ["ok"] }
      } }
      e {
        :content_type "text/plain"
        :body "ok"
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
;; handle-request (json)
(defonce env-json
  {
    :app "app"
    :path "test/test"
    :fmt "json"
    :config { :ok "ok" }
  })

(defonce deadline (t/plus (t/now) (t/minutes 10)))

(defn handle-request-entrypoint-json [req]
  (let [e {
        :config { :ok "ok" }
        :app "app"
        :path "test/test"
        :method "PUT"
        :headers { :ok ["ok"] }
        :request_url "http://test.com/r/app/test/test"
        :call_id 1
        :content_type "text/plain"
        :deadline deadline
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
    e { :result {
      :status 202
      :body { :ok "ok" }
      :headers { :h "h" }
      } :request v }]
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
    :config { :ok "ok" }
  })

(defn handle-request-entrypoint-cloudevent [req]
  (let [e {
        :config { :ok "ok" }
        :app "app"
        :path "test/test"
        :method "PUT"
        :headers { :ok ["ok"] }
        :request_url "http://test.com/r/app/test/test"
        :call_id 1
        :content_type "text/plain"
        :deadline deadline
        :data "hi"
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
    (is (= e req))
    {
        :status 202
        :body { :ok "ok" }
        :headers { :h "h" }
    }))

(deftest handle-request-cloudevent
  (with-redefs [env env-cloudevent]
    (let [v {
        :eventID 1
        :contentType "text/plain"
        :extensions { 
        :protocol {
              :method "PUT"
              :headers { :ok ["ok"] }
              :request_url "http://test.com/r/app/test/test"
          }
          :deadline deadline }
        :data "hi"
    }
    r (handle-request v handle-request-entrypoint-cloudevent)
    e { :result {
      :status 202
      :body { :ok "ok" }
      :headers { :h "h" }
      } :request v }]
    (is (= r e)))))

;;
;;
;;
;; handle-result (json, using cloudevent)
(deftest handle-result-json-ce
  (with-redefs [env env-cloudevent]
    (let [v { :result {
      :body { :ok "ok" }
      :status 202
      :headers { :ok "ok" }
      } :request {
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
(defn handle-request-entrypoint-exception [ctx]
  (/ 1 0))

(deftest handle-result-exception
  (with-redefs [env env-cloudevent]
    (let [r (handle-result (handle-request {} handle-request-entrypoint-exception))
          e { :contentType "application/json" 
              :data {}
              :extensions {
                :protocol {
                  :status_code 500
                  :headers {}
                  }}}]
      (is (= r e)))))
