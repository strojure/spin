(ns strojure.spin.server
  (:require [strojure.spin.impl.handler :as handler]))

(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defprotocol ServerAdapter
  "Methods of the concrete server implementation."

  (deliver-context
    [server context]
    "Delivers the given `context` when the handler chain completes.")

  (deliver-throwable
    [server context throwable]
    "Delivers the `throwable` when the handling of the `context` fails.")

  (in-nio-thread?
    [server]
    "Returns `true` if the execution is operating within the NIO thread.")

  (dispatch-blocking
    [server f]
    "Dispatches the invocation of the `(f)` on a non-NIO thread.")

  (dispatch-async
    [server f]
    "Dispatches the invocation of the `(f)` in an asynchronous context."))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private ^ThreadLocal thread-nio!
  (ThreadLocal.))

(defn thread-nio?
  "Returns `true` if the current handler execution is operating within the NIO
  thread."
  []
  (.get thread-nio!))

(defmacro ^:private in
  "Executes the `expr` in the context of `context` and throws an ex-info
  exception on error."
  [expr context]
  `(try ~expr (catch Throwable t#
                (throw (ex-info "Context handler error" {::context ~context ::throwable t#})))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn handle
  "Executes the `context` through the chain of `handlers` using the specified
  `server` adapter implementation. The result is delivered via
  [[deliver-context]] or [[deliver-throwable]]."
  [server context handlers]
  (letfn [(reduce* [context prev-context chain]
            (try
              (let [nio (in-nio-thread? server)]
                (when nio (.set thread-nio! nio))
                (loop [result context, prev prev-context, chain (seq chain)]
                  (cond
                    result
                    (if-let [context (-> (handler/result-context result) (in prev))]
                      (if chain
                        (if-let [handler (.first chain)]
                          (let [handler-type (handler/handler-type handler)]
                            (cond
                              ;; non-blocking handler function
                              (not handler-type)
                              (recur (-> (handler context) (in context)) context (.next chain))
                              ;; blocking handler
                              (.equals :blocking handler-type)
                              (if nio
                                (-> (dispatch-blocking server (^:once fn* []
                                                                (reduce* (try (handler/invoke-blocking handler context)
                                                                              (catch Throwable t t))
                                                                         context (.next chain))))
                                    (in context))
                                (recur (-> (handler/invoke-blocking handler context) (in context)) context (.next chain)))
                              ;; async handler
                              (.equals :async handler-type)
                              (-> (dispatch-async server (^:once fn* []
                                                           (try (handler/invoke-async handler context (fn [result] (reduce* result context (.next chain))))
                                                                (catch Throwable t
                                                                  (reduce* t context (.next chain))))))
                                  (in context))
                              :else
                              (-> (throw (ex-info (str "Invalid handler type: " handler-type) {}))
                                  (in context))))
                          ;; handler is falsy, skip
                          (recur result prev (.next chain)))
                        ;; chain is empty, complete
                        (if-let [chain+ (:spin/response-handlers context)]
                          (recur (dissoc context :spin/response-handlers) prev (seq chain+))
                          (deliver-context server context)))
                      ;; threat result as handler chain, or fail
                      (recur prev nil (-> (handler/result-chain result chain) (in prev))))
                    prev
                    (recur prev nil chain)
                    :else
                    (throw (ex-info "Handle empty context" {::chain chain})))))
              (catch Throwable t
                (let [{::keys [context throwable]
                       :or {context context, throwable t}} (ex-data t)]
                  (if-let [error-handlers (seq (:spin/error-handlers context))]
                    (try
                      ;; remove `:spin/error-handlers` from context just in case if error handlers fail
                      (let [context* (dissoc context :spin/error-handlers)]
                        (->> (concat error-handlers (handler/as-handler-seq (constantly throwable)))
                             (map #(fn as-handler [ctx]
                                     ;; check if prev handler returns new context
                                     (if (identical? ctx context*)
                                       (% ctx throwable)
                                       (reduced ctx))))
                             (reduce* context* nil)))
                      (catch Throwable t
                        (deliver-throwable server context t)))
                    (deliver-throwable server context throwable)))))
            ;; always return nil, provide result to `adapter`
            nil)]
    (assert (map? context) (str "Requires context map to apply handlers "
                                {:context context :handlers handlers}))
    (reduce* context nil (some-> handlers (handler/as-handler-seq)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
