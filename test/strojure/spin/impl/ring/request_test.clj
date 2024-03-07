(ns strojure.spin.impl.ring.request-test
  (:require
    [clojure.test :as test :refer [deftest testing]]
    [strojure.spin.impl.ring.request :as ring]
    [strojure.spin.request :as request])
  (:import
    (java.io ByteArrayInputStream)))

(set! *warn-on-reflection* true)

(declare thrown?)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest as-spin-request-t

  (testing `request/method
    (test/is (= "GET"
                (request/method (ring/as-spin-request {:request-method :get}))))
    (test/is (= "POST"
                (request/method (ring/as-spin-request {:request-method :post}))))
    (test/is (= "PUT"
                (request/method (ring/as-spin-request {:request-method :put}))))
    (test/is (= "DELETE"
                (request/method (ring/as-spin-request {:request-method :delete})))))

  (testing `request/scheme
    (test/is (= "http"
                (request/scheme (ring/as-spin-request {:scheme :http}))))
    (test/is (= "https"
                (request/scheme (ring/as-spin-request {:scheme :https})))))

  (testing `request/host
    (test/is (= "test.com"
                (request/host (ring/as-spin-request {:server-name "test.com"})))))

  (testing `request/port
    (test/is (= 8080
                (request/port (ring/as-spin-request {:server-port 8080})))))

  (testing `request/path
    (test/is (= "/test/path"
                (request/path (ring/as-spin-request {:uri "/test/path"})))))

  (testing `request/query-string
    (test/is (= "a=1&b=2"
                (request/query-string (ring/as-spin-request {:query-string "a=1&b=2"}))))
    (test/is (= nil
                (request/query-string (ring/as-spin-request {:query-string ""})))))

  (testing `request/protocol
    (test/is (nil? (request/protocol (ring/as-spin-request {})))))

  (testing `request/header
    (test/is (= "1"
                (request/header (ring/as-spin-request {:headers {"x-test" "1"}})
                                "X-Test"))))

  (testing `request/header*
    (test/is (= (list "1")
                (request/header* (ring/as-spin-request {:headers {"x-test" "1"}})
                                 "X-Test"))))

  (testing `request/header-names
    (test/is (= #{"host" "x-test"}
                (-> (request/header-names (ring/as-spin-request {:headers {"host" "test.com:8080"
                                                                           "x-test" "1"}}))
                    (set)))))

  (testing `request/body
    (test/is (= "test body"
                (-> (request/body (ring/as-spin-request
                                    {:body (ByteArrayInputStream. (.getBytes "test body"))}))
                    (.readAllBytes)
                    (String.)))))

  (testing `request/remote-addr
    (test/is (= "127.0.0.1"
                (request/remote-addr (ring/as-spin-request {:remote-addr "127.0.0.1"})))))

  (testing `request/remote-host
    (test/is (nil? (request/remote-host (ring/as-spin-request {})))))

  (testing `request/memoized
    (test/is (= "/test/path"
                (request/memoized (ring/as-spin-request {:uri "/test/path"})
                                  ::path request/path)))
    (test/is (= {::path "/test/path"}
                (-> (ring/as-spin-request {:uri "/test/path"})
                    (doto (request/memoized ::path request/path))
                    (request/memoized)
                    (select-keys [::path]))))
    (test/is (= {::path nil}
                (-> (ring/as-spin-request {})
                    (doto (request/memoized ::path request/path))
                    (request/memoized)
                    (select-keys [::path])))))

  )
