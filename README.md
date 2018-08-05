### Fn FDK for Clojure

[![CircleCI](https://circleci.com/gh/unpause-live/fdk-clj/tree/master.svg?style=svg)](https://circleci.com/gh/unpause-live/fdk-clj/tree/master)

#### About Fn

https://fnproject.io

The Fn project is an open-source container-native serverless platform that you can run anywhere -- any cloud or on-premise. It’s easy to use, supports every programming language, and is extensible and performant.


#### Installation

Add `[unpause/fdk-clj "1.0.6"]` to your dependencies.


#### Usage

Add `[fdk-clj.core :as fdk]` to the requirements list where your function's `main` entrypoint is.

Create an function handler to be called when a new request is made:

```
(defn handler [context data]
  "hey")
```

Or if you prefer to return a different format or add headers:
```
(defn handler [context data]
  ; stuff
  (fdk/raw-response {
    :status 200
    :body "blah"
    :headers {
      :content-type "text/plain"
      "X-Something-Different" "Yep"
    }}))
```

Create a `main` function like so:

```
(defn -main [& args]
  (fdk/handle handler))
```

#### Context format

```
 {
   :app_name "app"
   :app_route "/func"
   :call_id "id"
   :config { "FN_APP_NAME" "app" "FN_PATH" "func" ... env variables ... }
   :headers { http headers }
   :arguments {}
   :fn_format "cloudevent"
   :execution_type "sync"
   :deadline "2018-01-01T23:59:59.999"
   :content_type "application/json"
   :request_url "http://domain.com/r/app/func"
   [optional] :cloudevent { event map } ;; only applies to CloudEvent Format
 }
```

Requests that go beyoned `deadline` will be forcefully killed.

#### GraalVM

For fast(er) cold-start times you should use GraalVM's Native Image with FROM SCRATCH for the docker image.
