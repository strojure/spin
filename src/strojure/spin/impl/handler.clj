(ns strojure.spin.impl.handler
  (:import (clojure.lang Fn IPersistentMap IPersistentVector RT Reduced Sequential)
           (java.util.concurrent CompletableFuture)
           (java.util.function BiConsumer)))

(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defprotocol ResultContext
  "Represents a handler result that carries a context map."

  (result-context [result]
    "Returns the context map from the handler result or `nil`.
    Throws an exception for error result."))

(defprotocol ResultChain
  "Represents a handler result that carries a change in the handler chain."

  (result-chain [result handlers]
    "Returns a new handler chain for the given sequence of remaining `handlers`.

    This function is invoked for handler results when `(result-context result)`
    is `nil`. The implementation can add handler(s) before the sequence, such as
    for blocking or async execution, or drop the sequence for short-circuiting
    as in `Reduced`."))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defprotocol HandlerType
  "Represents an invokable handler function type."

  (handler-type [handler]
    "Returns the handler function type:
    - `nil` for ordinary Clojure functions.
    - `:blocking` for functions that should be invoked on a non-NIO thread
      using [[invoke-blocking]].
    - `:async` for functions that return results via an async callback
      using [[invoke-async]]."))

(defprotocol HandlerBlocking
  "Represents a blocking handler that should be invoked on a non-NIO thread."

  (invoke-blocking [handler context]
    "Returns the handler result for the given `context`. This function should
    be invoked on a non-NIO thread."))

(defprotocol HandlerAsync
  "Represents an async handler that returns results via an async callback."

  (invoke-async [handler context callback]
    "Executes the handler asynchronously and returns results via a callback
    function `(fn callback [result] ...)`."))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defprotocol AsHandlerSeq
  "Methods for manipulations with a sequence of handlers, optimized for object type."

  (as-handler-seq [x]
    "Returns object `x` as a handler sequence.")

  (prepend-seq [x to]
    "Returns a handler sequence with `x` prepended to `to`.")

  (append-vec [x to]
    "Appends handlers `x` to the vector `to`."))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Persistent map is a context map.
(extend-type IPersistentMap
  ResultContext,,, (result-context [m] m))

;; Exceptions is a context map error.
(extend-type Throwable
  ResultContext,,, (result-context [t] (throw t)))

;; `Reduced` represents the last result in the handler chain.
(extend-type Reduced
  ResultContext,,, (result-context [_] nil)
  ResultChain,,,,, (result-chain [x _] (RT/list (fn [_] (.deref x)))))

;; Function is an 1-item handler seq.
(extend-type Fn
  ResultContext,,, (result-context [_] nil)
  ResultChain,,,,, (result-chain [f handlers] (cons f handlers))
  HandlerType,,,,, (handler-type [_] nil)
  AsHandlerSeq,,,,
  (as-handler-seq [x] (RT/list x))
  (prepend-seq [x to] (cons x to))
  (append-vec [f to] (as-> (or to []) handlers
                           (.cons ^IPersistentVector handlers f))))

;; Sequential is a handler seq.
(extend-type Sequential
  ResultContext,,, (result-context [_] nil)
  ResultChain,,,,, (result-chain [xs handlers] (concat xs handlers))
  AsHandlerSeq,,,,
  (as-handler-seq [xs] xs)
  (prepend-seq [xs to] (reduce conj to (seq xs)))
  (append-vec [xs to] (reduce #(.cons ^IPersistentVector %1 %2) (or to []) xs)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Custom type to mark function `f` as blocking handler.
(deftype Blocking [f]
  HandlerType,,,,,,, (handler-type [_] :blocking)
  HandlerBlocking,,, (invoke-blocking [_ x] (f x))
  ResultChain,,,,,,, (result-chain [this handlers] (cons this handlers))
  ResultContext,,,,, (result-context [_] nil))

;; CompletableFuture as async handler.
(extend-type CompletableFuture
  HandlerType,,,,, (handler-type [_] :async)
  HandlerAsync,,,, (invoke-async [ft _ callback]
                     (.whenComplete ft (reify BiConsumer (accept [_ v e]
                                                           (callback (if e (or (ex-cause e) e)
                                                                           v)))))
                     nil)
  ResultChain,,,,, (result-chain [ft handlers] (cons ft handlers))
  ResultContext,,, (result-context [ft] (when (.isDone ft)
                                          (some-> (.getNow ft nil) (result-context)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(comment
  (prepend-seq identity nil)
  (prepend-seq [identity] nil)
  (append-vec identity nil)
  (append-vec [identity] nil)
  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
