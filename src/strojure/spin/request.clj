(ns strojure.spin.request
  (:require
    [strojure.spin.impl.request :as impl.request])
  (:import
    (java.io InputStream)))

(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Implementation dependent functions

(defn method
  "Returns the name of the HTTP method with which the request was made, for
  example, \"GET\", \"POST\", or \"PUT\"."
  {:inline (fn [request] `(impl.request/method ~request))
   :added "1.0"}
  [request]
  (impl.request/method request))

(defn scheme
  "Returns the name of the scheme used to make the request, for example,
    \"http\", \"https\" or \"ftp\"."
  {:inline (fn [request] `(impl.request/scheme ~request))
   :added "1.0"}
  [request]
  (impl.request/scheme request))

(defn host
  "Returns the host name of the server to which the request was sent."
  {:inline (fn [request] `(impl.request/host ~request))
   :added "1.0"}
  [request]
  (impl.request/host request))

(defn port
  "Returns the port number to which the request was sent."
  {:inline (fn [request] `(impl.request/port ~request))
   :added "1.0"}
  [request]
  (impl.request/port request))

(defn path
  "The part of the request’s URL from the protocol name up to the query string
  in the first line of the HTTP request."
  {:inline (fn [request] `(impl.request/path ~request))
   :added "1.0"}
  [request]
  (impl.request/path request))

(defn query-string
  "Returns the part of the request’s URL after the '?' character. Returns `nil`
  for the empty query string."
  {:inline (fn [request] `(impl.request/query-string ~request))
   :added "1.0"}
  [request]
  (impl.request/query-string request))

(defn protocol
  "Returns name and version of the protocol with which the request was sent,
  for example \"HTTP/1.1\"."
  {:inline (fn [request] `(impl.request/protocol ~request))
   :added "1.0"}
  [request]
  (impl.request/protocol request))

(defn header
  "Returns the value of the _first_ specified request header as a String, or
  `nil`. The header name is _case-insensitive_. Use [[header*]] for getting
  multiple header values."
  {:inline (fn [request header-name] `(impl.request/header ~request ~header-name))
   :added "1.0"}
  [request header-name]
  (impl.request/header request header-name))

(defn header*
  "Returns all the values of the specified request header as a seq of strings."
  {:inline (fn [request header-name] `(impl.request/header* ~request ~header-name))
   :added "1.0"}
  [request header-name]
  (impl.request/header* request header-name))

(defn header-names
  "Returns a seq of all the header names this request contains."
  {:inline (fn [request] `(impl.request/header-names ~request))
   :added "1.0"}
  [request]
  (impl.request/header-names request))

(defn body
  "Returns an InputStream for the request body, if present."
  {:inline (fn [request] `(impl.request/body ~request))
   :added "1.0"}
  ^InputStream [request]
  (impl.request/body request))

(defn remote-addr
  "Returns the IP address of the client or the last proxy that sent the
  request."
  {:inline (fn [request] `(impl.request/remote-addr ~request))
   :added "1.0"}
  [request]
  (impl.request/remote-addr request))

(defn remote-host
  "Returns the fully qualified name of the client or the last proxy that sent
  the request."
  {:inline (fn [request] `(impl.request/remote-host ~request))
   :added "1.0"}
  [request]
  (impl.request/remote-host request))

(defn memoized
  "Returns memoized value associated with the key `k`. If the value is not
  computed yet, initializes it using function `(fn init-fn [request] ...)`.
  1-arity invocation returns map of all memoized values."
  {:inline (fn [request k init-fn] `(impl.request/memoized ~request ~k ~init-fn))
   :inline-arities #{3}
   :added "1.0"}
  ([request k init-fn]
   (impl.request/memoized request k init-fn))
  ([request]
   (impl.request/memoized request)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Extended functionality

(defn body-bytes!
  "Returns request body consumed as byte array. The result is memoized.
  (!) The result of [[body]] cannot be used after invocation of `body-bytes!`."
  {:added "1.0"}
  ^bytes [request]
  (memoized request ::body-bytes (fn read-all-bytes [request] (some-> (body request) (.readAllBytes)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Dev utility

#_{:clj-kondo/ignore [:shadowed-var]}

(defrecord RequestInfo
  [method scheme host port path query-string protocol headers memoized])

(defn inspect
  "Returns [[RequestInfo]] map with all request data. The record is used to
  present inspected keys in order."
  {:added "1.0"}
  [request]
  (RequestInfo.
    (method request)
    (scheme request)
    (host request)
    (port request)
    (path request)
    (query-string request)
    (protocol request)
    (into [] (comp (map (fn [header-name]
                          (mapv (fn [v] [header-name v])
                                (header* request header-name))))
                   cat)
          (header-names request))
    (memoized request)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
