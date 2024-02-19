(ns strojure.spin.handler
  (:require [strojure.spin.impl.handler :as handler]
            [strojure.spin.server :as server])
  (:import (clojure.lang ILookup)))

(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn blocking
  "Returns a handler that ensures the execution of the `handler` on a non-NIO
  thread."
  [handler]
  (fn [context]
    (if (server/thread-nio?)
      (handler/->Blocking handler)
      (handler context))))

(defn set-error-handler
  "Adds the error handler `f` to the `context`. An error handler is a function
  `(fn error-handler [context throwable] new-context-or-nil)` that can provide
  a new context as a handler result instead of throwing an exception. Multiple
  error handlers are executed from last to first and short-circuit on the first
  result that returns a new context."
  [context f]
  (assoc context :spin/error-handlers
                 (handler/prepend-seq f (some-> ^ILookup context (.valAt :spin/error-handlers)))))

(defn set-response-handler
  "Adds a context handler to the `context` to be applied to the result of the
  handler chain. This enables 'post-response' tasks, such as adding response
  headers or logging the response. Response handlers are executed from first to
  last."
  [context f]
  (assoc context :spin/response-handlers
                 (handler/append-vec f (some-> ^ILookup context (.valAt :spin/response-handlers)))))

(comment
  (set-error-handler {} identity)
  (set-error-handler {} [identity])
  (set-response-handler {} identity)
  (set-response-handler {} [identity])
  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn sample
  "Executes the `context` through the chain of `handlers` using a simple server
  implementation. Returns the handling result or throws an exception.

  Simple adapter:
  - pretends to start execution in NIO thread.
  - dispatches blocking handler to `future`.
  - executes async handler “as is”.
  - prints exception message in `deliver-throwable`.
  "
  [context handlers]
  (let [p (promise) nio! (atom true)]
    (-> (reify server/ServerAdapter
          (deliver-context [_ context] (deliver p context))
          (deliver-throwable [_ context throwable]
            (println context "-> deliver-throwable:" (ex-message throwable))
            (deliver p throwable))
          (in-nio-thread? [_] @nio!)
          (dispatch-blocking [_ f] (future (reset! nio! false) (f)))
          (dispatch-async [_ f] (f)))
        (server/handle context handlers))
    (-> (deref p 1000 ::timed-out)
        (handler/result-context))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
