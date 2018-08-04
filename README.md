### Fn FDK for Clojure

[![CircleCI](https://circleci.com/gh/unpause-live/fdk-clj/tree/master.svg?style=svg)](https://circleci.com/gh/unpause-live/fdk-clj/tree/master)

#### Installation

Add `[unpause/fdk-clj "1.0.0-SNAPSHOT"]` to your dependencies.


#### Usage

Add `[fdk-clj.core :as fdk]` to the requirements list where your function's `main` entrypoint is.

Create an function handler to be called when a new request is made:

```
(defn func-entrypoint [request]
  ; stuff
  {
    :status 200
  })
```

Create a `main` function like so:

```
(defn -main [& args]
  (fdk/handle func-entrypoint))
```

#### Request format

```
 {
   :config { "FN_APP_NAME" "app" "FN_PATH" "func" ... env variables ... }
   :content_type "application/json"
   :deadline "2018-01-01T23:59:59.999"
   :call_id "id"
   :app "app"
   :path "func"
   :method "GET"
   :headers { http headers}
   :request_url "http://domain.com/r/app/func"
   :data "body data"
   [optional] :cloudevent { event map } ;; only applies to CloudEvent Format
 }
```

Requests that go beyoned `deadline` will be forcefully killed.