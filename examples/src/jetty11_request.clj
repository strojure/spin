(ns jetty11-request
  (:require
    [strojure.spin.impl.request :as impl.request]
    [strojure.spin.request :as request])
  (:import
    (jakarta.servlet.http HttpServletRequest HttpServletResponse)
    (java.net URI)
    (java.net.http HttpClient HttpRequest HttpRequest$BodyPublishers HttpResponse$BodyHandlers)
    (org.eclipse.jetty.server Request Server ServerConnector)
    (org.eclipse.jetty.server.handler AbstractHandler)))

(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce ^{:doc "The attachment key for the map of memoized values."}
  MEMOIZED_KEY (str *ns* "/memoized"))

(extend-type HttpServletRequest

  impl.request/HttpMessage
  (method
    [this] (.getMethod this))
  (scheme
    [this] (.getScheme this))
  (host
    [this] (.getServerName this))
  (port
    [this] (.getServerPort this))
  (path
    [this] (.getRequestURI this))
  (query-string
    [this] (.getQueryString this))
  (protocol
    [this] (.getProtocol this))
  (header
    [this header-name]
    (.getHeader this header-name))
  (header*
    [this header-name]
    (some-> (.getHeaders this header-name)
            (.asIterator)
            (iterator-seq)))
  (header-names
    [this]
    (-> (.getHeaderNames this)
        (.asIterator)
        (iterator-seq)))
  (body
    [this]
    (.getInputStream this))

  impl.request/ServerRequest
  (remote-addr
    [this] (.getRemoteAddr this))
  (remote-host
    [this] (.getRemoteHost this))

  impl.request/Memoized
  (memoized
    ([this]
     (.getAttribute this MEMOIZED_KEY))
    ([this k init]
     (let [m (.getAttribute this MEMOIZED_KEY)
           none (Object.)
           value (k m none)]
       (if (.equals none value)
         (doto (init this)
           (as-> v (.setAttribute this MEMOIZED_KEY (assoc m k v))))
         value)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- server-url
  "Returns URL for the server running on a random port."
  [^Server server, path]
  (let [port (.getLocalPort ^ServerConnector (first (.getConnectors server)))]
    (str
      "http://localhost:" port path)))

(defn- http-post
  [url body]
  (let [body (.body (.send (HttpClient/newHttpClient)
                           (-> (HttpRequest/newBuilder)
                               (.header "X-Test" "1")
                               (.header "X-Test" "2")
                               (.uri (URI. url))
                               (.POST (HttpRequest$BodyPublishers/ofString body))
                               (.build))
                           (HttpResponse$BodyHandlers/ofString)))]
    (println url "\n ->" body)
    body))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Start web server and execute HTTP request

(declare -request)

(let [handler (proxy [AbstractHandler] []
                (handle [_target, ^Request jetty-request
                         ^HttpServletRequest request
                         ^HttpServletResponse response]
                  (def -request
                    ;; We collect request data here because `request` is not valid after handling
                    (let [body-string (String. (request/body-bytes! request))]
                      {:request request
                       :inspect (request/inspect request)
                       :extra {:body-string body-string
                               :remote-addr (request/remote-addr request)
                               :x-test-header (request/header request "x-test")
                               :x-test-header* (request/header* request "x-test")}}))
                  (-> (.getWriter response)
                      (.print "OK"))
                  (.setHandled jetty-request true)))
      server (Server. 0)]
  (.setHandler server handler)
  (.start server)
  (http-post (server-url server "/test-path?a=1&b=2")
             "test-body")
  (.stop server))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
