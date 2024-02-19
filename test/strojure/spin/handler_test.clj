(ns strojure.spin.handler-test
  (:refer-clojure :exclude [error-handler])
  (:require [clojure.test :as test :refer [deftest testing]]
            [strojure.spin.handler :as handler])
  (:import (java.util.concurrent CompletableFuture)
           (java.util.function Supplier)))

(set! *warn-on-reflection* true)

(declare thrown? thrown-with-msg?)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- sample
  [context & handlers]
  (handler/sample context handlers))

(defn- handler
  "Returns handler which puts `x` in context’s :result vector.
  Wraps result with the optional `:wrap`.
  When `x` is a function it is invoked to get a value to put in the context.

  Options:

  - `:handler-type`
      - `:async` returns async handler
      - `:blocking` returns blocking handler

  - `:wrap` the function to wrap result context with.
  "
  ([x]
   (fn put [ctx]
     (update ctx :result (fnil conj []) (if (fn? x) (x) x))))
  ([x {:keys [handler-type wrap] :as opts}]
   (case handler-type
     :async (fn put-async [ctx]
                      (CompletableFuture/supplyAsync
                        (reify Supplier (get [_] ((handler x (dissoc opts :handler-type)) ctx)))))
     :blocking (fn put-blocking [_ctx]
                         (handler/blocking (handler x (dissoc opts :handler-type))))
     (if wrap
       (comp wrap (handler x))
       (handler x)))))

(defn- error-handler
  "Returns error handler which adds the message of throwable `t` in the contexts
  under the `tag` key."
  [tag]
  (fn [ctx t] ((handler {tag (ex-message t)}) ctx)))

(defn- same-thread-fn
  "Returns function which tests if execution is in the same thread."
  []
  (let [current-thread (Thread/currentThread)]
    (fn same-thread? [] (= current-thread (Thread/currentThread)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest require-context-is-map

  (test/is (thrown? AssertionError (-> nil (handler/sample (handler :a)))))
  (test/is (thrown? AssertionError (-> (fn []) (handler/sample (handler :a)))))

  )

(deftest basic-handler-sequence

  (test/is (= {:result [:a]}
              (-> {} (sample (handler :a)))))

  (test/is (= {:result [:a :b]}
              (-> {} (sample (handler :a)
                             (handler :b)))))

  (test/is (= {:result [:a :b :c]}
              (-> {} (sample (handler :a)
                             (handler :b)
                             (handler :c)))))

  (testing "Empty handler sequence"
    (test/is (= {}
                (-> {} (sample)))))

  (testing "Handler returning nil is skipped"
    (test/is (= {:result [:a :c]}
                (-> {} (sample (handler :a)
                               (handler :b {:wrap (constantly nil)})
                               (handler :c))))))

  )

(deftest single-handler-as-sequence

  (test/is (= {:result [:a]}
              (-> {} (handler/sample (handler :a)))))

  )

(deftest handler-with-reduced-result

  (test/is (= {:result [:a]}
              (-> {} (sample (handler :a {:wrap reduced})
                             (handler :b)
                             (handler :c)))))

  (test/is (= {:result [:a :b]}
              (-> {} (sample (handler :a)
                             (handler :b {:wrap reduced})
                             (handler :c)))))

  (test/is (= {:result [:a :b :c]}
              (-> {} (sample (handler :a)
                             (handler :b)
                             (handler :c {:wrap reduced})))))

  )

(deftest blocking-handler

  (test/is (= {:result [:a]}
              (-> {} (sample (handler :a {:handler-type :blocking})))))

  (test/is (= {:result [:a :b]}
              (-> {} (sample (handler :a {:handler-type :blocking})
                             (handler :b)))))

  (test/is (= {:result [:a :b]}
              (-> {} (sample (handler :a)
                             (handler :b {:handler-type :blocking})))))

  (test/is (= {:result [:a :b]}
              (-> {} (sample (handler :a {:handler-type :blocking})
                             (handler :b {:handler-type :blocking})))))

  (testing "Blocking execution in another thread"

    (test/is (= {:result [true]}
                (-> {} (sample (handler (same-thread-fn))))))

    (test/is (= {:result [false]}
                (-> {} (sample (handler (same-thread-fn) {:handler-type :blocking})))))

    (test/is (= {:result [true true]}
                (-> {} (sample (handler (same-thread-fn))
                               (handler (same-thread-fn))))))

    (test/is (= {:result [true false]}
                (-> {} (sample (handler (same-thread-fn))
                               (handler (same-thread-fn) {:handler-type :blocking})))))

    (test/is (= {:result [false false]}
                (-> {} (sample (handler (same-thread-fn) {:handler-type :blocking})
                               (handler (same-thread-fn))))))

    (test/is (= {:result [false false]}
                (-> {} (sample (handler (same-thread-fn) {:handler-type :blocking})
                               (handler (same-thread-fn) {:handler-type :blocking})))))

    )

  )

(deftest async-handler

  (test/is (= {:result [:a]}
              (-> {} (sample (handler :a {:handler-type :async})))))

  (test/is (= {:result [:a :b]}
              (-> {} (sample (handler :a {:handler-type :async})
                             (handler :b)))))

  (test/is (= {:result [:a :b]}
              (-> {} (sample (handler :a)
                             (handler :b {:handler-type :async})))))

  (test/is (= {:result [:a :b]}
              (-> {} (sample (handler :a {:handler-type :async})
                             (handler :b {:handler-type :async})))))

  (testing "Async execution in another thread"

    (test/is (= {:result [true]}
                (-> {} (sample (handler (same-thread-fn))))))

    (test/is (= {:result [false]}
                (-> {} (sample (handler (same-thread-fn) {:handler-type :async})))))

    (test/is (= {:result [true true]}
                (-> {} (sample (handler (same-thread-fn))
                               (handler (same-thread-fn))))))

    (test/is (= {:result [true false]}
                (-> {} (sample (handler (same-thread-fn))
                               (handler (same-thread-fn) {:handler-type :async})))))

    (test/is (= {:result [false false]}
                (-> {} (sample (handler (same-thread-fn) {:handler-type :async})
                               (handler (same-thread-fn))))))

    (test/is (= {:result [false false]}
                (-> {} (sample (handler (same-thread-fn) {:handler-type :async})
                               (handler (same-thread-fn) {:handler-type :async})))))

    )

  )

(deftest handler-returning-handlers

  (test/is (= {:result [:a]}
              (-> {} (sample (fn [_ctx] (handler :a))))))

  (test/is (= {:result [:a :b]}
              (-> {} (sample (fn [_ctx] (handler :a))
                             (handler :b)))))

  (test/is (= {:result [:a :b :c]}
              (-> {} (sample (fn [_ctx] (list (handler :a)
                                              (handler :b)))
                             (handler :c)))))

  (test/is (= {:result [:c]}
              (-> {} (sample (fn [_ctx] (list))
                             (handler :c)))))

  )

(deftest handler-exceptions

  (testing "Thrown exceptions"

    (test/is (thrown-with-msg?
               Exception #"^Message a$"
               (-> {} (sample (handler (fn [] (throw (Exception. "Message a"))))))))

    (test/is (thrown-with-msg?
               Exception #"^Message a$"
               (-> {} (sample (handler (fn [] (throw (Exception. "Message a"))))
                              (handler :b)))))

    (test/is (thrown-with-msg?
               Exception #"^Message a$"
               (-> {} (sample (handler (fn [] (throw (Exception. "Message a"))))
                              (handler (fn [] (throw (Exception. "Message b"))))))))

    (test/is (thrown-with-msg?
               Exception #"^Message b$"
               (-> {} (sample (handler :a)
                              (handler (fn [] (throw (Exception. "Message b"))))))))

    (test/is (thrown-with-msg?
               Exception #"^Message b$"
               (-> {} (sample (handler :a)
                              (handler (fn [] (throw (Exception. "Message b"))))
                              (handler :c)))))

    (test/is (thrown-with-msg?
               Exception #"^Message b$"
               (-> {} (sample (handler :a)
                              (handler (fn [] (throw (Exception. "Message b")))
                                       {:handler-type :async})
                              (handler :c)))))

    (test/is (thrown-with-msg?
               Exception #"^Message b$"
               (-> {} (sample (handler :a)
                              (handler (fn [] (throw (Exception. "Message b")))
                                       {:handler-type :blocking})
                              (handler :c)))))

    )

  (testing "Returned exceptions"

    (test/is (thrown-with-msg?
               Exception #"^Message a$"
               (-> {} (sample (handler :a {:wrap (fn [_ctx] (Exception. "Message a"))})))))

    (test/is (thrown-with-msg?
               Exception #"^Message a$"
               (-> {} (sample (handler :a {:wrap (fn [_ctx] (Exception. "Message a"))})
                              (handler :b)))))

    (test/is (thrown-with-msg?
               Exception #"^Message a$"
               (-> {} (sample (handler :a {:wrap (fn [_ctx] (Exception. "Message a"))})
                              (handler :b {:wrap (fn [_ctx] (Exception. "Message b"))})))))

    (test/is (thrown-with-msg?
               Exception #"^Message b$"
               (-> {} (sample (handler :a)
                              (handler :b {:wrap (fn [_ctx] (Exception. "Message b"))})))))

    (test/is (thrown-with-msg?
               Exception #"^Message b$"
               (-> {} (sample (handler :a)
                              (handler :b {:wrap (fn [_ctx] (Exception. "Message b"))})
                              (handler :c)))))

    (test/is (thrown-with-msg?
               Exception #"^Message b$"
               (-> {} (sample (handler :a)
                              (handler :b {:wrap (fn [_ctx] (Exception. "Message b"))
                                           :handler-type :async})
                              (handler :c)))))

    (test/is (thrown-with-msg?
               Exception #"^Message b$"
               (-> {} (sample (handler :a)
                              (handler :b {:wrap (fn [_ctx] (Exception. "Message b"))
                                           :handler-type :blocking})
                              (handler :c)))))

    )

  )

(deftest set-error-handler-t

  (test/is (= {:result [{:e "Message"}]}
              (-> {} (sample (fn [ctx] (handler/set-error-handler ctx (error-handler :e)))
                             (handler (fn [] (throw (Exception. "Message"))))))))

  (test/is (= {:result [:a {:e "Message"}]}
              (-> {} (sample (fn [ctx] (handler/set-error-handler ctx (error-handler :e)))
                             (handler :a)
                             (handler (fn [] (throw (Exception. "Message"))))))))

  (test/is (= {:result [:a {:e "Message"}]}
              (-> {} (sample (handler :a)
                             (fn [ctx] (handler/set-error-handler ctx (error-handler :e)))
                             (handler (fn [] (throw (Exception. "Message"))))))))

  (test/is (= {:result [{:e "Message"}]}
              (-> {} (sample (fn [ctx] (handler/set-error-handler ctx (error-handler :e)))
                             (handler (fn [] (throw (Exception. "Message"))))
                             (handler "Unreachable")))))

  (test/is (= {:result [{:e2 "Message"}]}
              (-> {} (sample (fn [ctx] (handler/set-error-handler ctx (error-handler :e1)))
                             (fn [ctx] (handler/set-error-handler ctx (error-handler :e2)))
                             (handler (fn [] (throw (Exception. "Message"))))))))

  (testing "Error handler returning context itself is skipped"
    (test/is (= {:result [{:e1 "Message"}]}
                (-> {} (sample (fn [ctx] (handler/set-error-handler ctx (error-handler :e1)))
                               (fn [ctx] (handler/set-error-handler ctx (fn [ctx _t] ctx)))
                               (handler (fn [] (throw (Exception. "Message")))))))))

  (testing "Error handler returning nil is skipped"
    (test/is (= {:result [{:e1 "Message"}]}
                (-> {} (sample (fn [ctx] (handler/set-error-handler ctx (error-handler :e1)))
                               (fn [ctx] (handler/set-error-handler ctx (constantly nil)))
                               (handler (fn [] (throw (Exception. "Message")))))))))

  (testing "Error handler returning another handler"
    (test/is (= {:result [{:e "Message"}]}
                (-> {} (sample (fn [ctx] (handler/set-error-handler ctx (fn [_ctx t] (handler {:e (ex-message t)}))))
                               (handler (fn [] (throw (Exception. "Message")))))))))

  (test/is (= {:result [{:e "Message"}]}
              (-> {} (sample (fn [ctx] (handler/set-error-handler ctx (error-handler :e)))
                             (handler (fn [] (throw (Exception. "Message")))
                                      {:handler-type :blocking})))))

  (test/is (= {:result [{:e "Message"}]}
              (-> {} (sample (fn [ctx] (handler/set-error-handler ctx (error-handler :e)))
                             (handler (fn [] (throw (Exception. "Message")))
                                      {:handler-type :async})))))

  )

(deftest set-response-handler-t

  (test/is (= {:result [:a]}
              (-> {} (sample (fn [ctx] (handler/set-response-handler ctx (handler :a)))))))

  (test/is (= {:result [:a :b]}
              (-> {} (sample (fn [ctx] (handler/set-response-handler ctx (handler :a)))
                             (fn [ctx] (handler/set-response-handler ctx (handler :b)))))))

  (test/is (= {:result [:a :b]}
              (-> {} (sample (handler :a)
                             (fn [ctx] (handler/set-response-handler ctx (handler :b)))))))

  (test/is (= {:result [:b :a]}
              (-> {} (sample (fn [ctx] (handler/set-response-handler ctx (handler :a)))
                             (handler :b)))))

  (test/is (= {:result [:a]}
              (-> {} (sample (handler :a {:wrap reduced})
                             (fn [ctx] (handler/set-response-handler ctx (handler :b)))))))

  (test/is (= {:result [:b :a]}
              (-> {} (sample (fn [ctx] (handler/set-response-handler ctx (handler :a)))
                             (handler :b {:wrap reduced})))))

  (test/is (= {:result [:b :a]}
              (-> {} (sample (fn [ctx] (handler/set-response-handler ctx (handler :a)))
                             (handler :b {:handler-type :blocking})))))

  (test/is (= {:result [:b :a]}
              (-> {} (sample (fn [ctx] (handler/set-response-handler ctx (handler :a)))
                             (handler :b {:handler-type :async})))))

  (test/is (thrown-with-msg?
             Exception #"^Message$"
             (-> {} (sample (fn [ctx] (handler/set-response-handler ctx (handler (fn [] (throw (Exception. "Message"))))))))))

  (test/is (thrown-with-msg?
             Exception #"^Message$"
             (-> {} (sample (handler :a)
                            (fn [ctx] (handler/set-response-handler ctx (handler (fn [] (throw (Exception. "Message"))))))))))

  )
