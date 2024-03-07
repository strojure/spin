(ns strojure.spin.impl.request)

(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defprotocol HttpMessage
  "HTTP Message
  https://developer.mozilla.org/en-US/docs/Web/HTTP/Messages#http_requests."

  (method
    [request]
    "Returns the name of the HTTP method with which the request was made, for
    example, \"GET\", \"POST\", or \"PUT\".")

  (scheme
    [request]
    "Returns the name of the scheme used to make the request, for example,
    \"http\", \"https\" or \"ftp\".")

  (host
    [request]
    "Returns the host name of the server to which the request was sent.")

  (port
    [request]
    "Returns the port number to which the request was sent.")

  (path
    [request]
    "Returns the part of the request’s URL from the protocol name up to the
    query string in the first line of the HTTP request.")

  (query-string
    [request]
    "Returns the part of the request’s URL after the '?' character. Returns
    `nil` for the empty query string.")

  (protocol
    [request]
    "Returns name and version of the protocol with which the request was sent,
    for example \"HTTP/1.1\".")

  (header
    [request header-name]
    "Returns the value of the _first_ specified request header as a String, or
    `nil`. The header name is _case-insensitive_.")

  (header*
    [request header-name]
    "Returns all the values of the specified request header as a seq of strings.")

  (header-names
    [request]
    "Returns a seq of all the header names this request contains.")

  (body
    ^java.io.InputStream [request]
    "Returns an InputStream for the request body, if present."))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defprotocol ServerRequest
  "Request data out of HTTP message."

  (remote-addr
    [request]
    "Returns the IP address of the client or the last proxy that sent the
    request.")

  (remote-host
    [request]
    "Returns the fully qualified name of the client or the last proxy that sent
    the request."))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defprotocol Memoized
  "Memoization of the computed request data."

  (memoized
    [request]
    [request key init-fn]
    "Returns memoized value associated with `key`. If the value is not computed
    yet, initializes it using function `(fn init-fn [request] ...)`. 1-arity
    invocation returns map of all memoized values."))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
