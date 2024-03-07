(ns strojure.spin.impl.ring.request
  "Implementation of the Spin request over [Ring request map][1]

  [1]: https://github.com/ring-clojure/ring/wiki/Concepts#requests
  "
  (:require
    [strojure.spin.impl.request :as impl.request]
    [strojure.spin.request :as request]))

(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord SpinRequest [request-map memoized!]

  impl.request/HttpMessage
  (method
    [_] (some-> (:request-method request-map) name .toUpperCase))
  (scheme
    [_] (some-> (:scheme request-map) name))
  (host
    [_] (:server-name request-map))
  (port
    [_] (:server-port request-map))
  (path
    [_] (:uri request-map))
  (query-string
    [_] (not-empty (:query-string request-map)))
  (protocol
    [_] (comment "Not implemented"))
  (header
    [_ header-name]
    (some-> (:headers request-map)
            (get (.toLowerCase ^String header-name))))
  (header*
    [this header-name]
    (when-let [v (request/header this header-name)] [v]))
  (header-names
    [_] (keys (:headers request-map)))
  (body
    [_] (:body request-map))

  impl.request/ServerRequest
  (remote-addr
    [_] (:remote-addr request-map))
  (remote-host
    [_] (comment "Not implemented"))

  impl.request/Memoized
  (memoized
    [_] (some-> memoized! deref))
  (memoized
    [this k init]
    (let [none (Object.) value (k @memoized! none)]
      (if (.equals none value)
        (doto (init this)
          (as-> v (swap! memoized! assoc k v)))
        value))))

(defn as-spin-request
  "Returns Spin request implementation for the Ring request map."
  [request-map]
  (SpinRequest. request-map (atom nil)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
