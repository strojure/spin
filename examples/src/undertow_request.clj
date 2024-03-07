(ns undertow-request
  "Example of the Spin request implementation over `io.undertow.server
  HttpServerExchange`."
  (:require
    [strojure.spin.impl.request :as impl.request]
    [strojure.spin.request :as request])
  (:import
    (io.undertow Undertow Undertow$ListenerInfo UndertowOptions)
    (io.undertow.server HttpHandler HttpServerExchange)
    (io.undertow.server.handlers BlockingHandler)
    (io.undertow.util AttachmentKey HttpString)
    (java.net InetSocketAddress URI)
    (java.net.http HttpClient HttpRequest HttpRequest$BodyPublishers HttpResponse$BodyHandlers)
    (java.util.function Function)))

(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce ^{:doc "The attachment key for the map of memoized values."}
  MEMOIZED_KEY (AttachmentKey/create Object))

(def ^:private http-string->string
  (reify Function
    (apply [_ s] (.toString ^HttpString s))))

(extend-type HttpServerExchange

  impl.request/HttpMessage
  (method
    [this]
    (-> (.getRequestMethod this)
        (.toString)))
  (scheme
    [this] (.getRequestScheme this))
  (host
    [this] (.getHostName this))
  (port
    [this] (.getPort (.getDestinationAddress this)))
  (path
    [this] (.getRequestPath this))
  (query-string
    [this]
    (as-> (.getQueryString this) s
      (when-not (.isEmpty s) s)))
  (protocol
    [this]
    (-> (.getProtocol this)
        (.toString)))
  (header
    [this header-name]
    (-> (.getRequestHeaders this)
        (.getFirst ^String header-name)))
  (header*
    [this header-name]
    (-> (.getRequestHeaders this)
        (.get ^String header-name)))
  (header-names
    [this]
    (-> (.getHeaderNames (.getRequestHeaders this))
        (.stream) (.map http-string->string) (.toArray)
        (seq)))
  (body
    [this]
    (when-not (.isRequestComplete this)
      (when-not (.isBlocking this)
        (.startBlocking this))
      (.getInputStream this)))

  impl.request/ServerRequest
  (remote-addr
    [this]
    (.getHostAddress (.getAddress (.getSourceAddress this))))
  (remote-host
    [this]
    (.getHostName (.getAddress (.getSourceAddress this))))

  impl.request/Memoized
  (memoized
    ([this]
     (.getAttachment this MEMOIZED_KEY))
    ([this k init]
     (let [m (.getAttachment this MEMOIZED_KEY)
           none (Object.)
           value (k m none)]
       (if (.equals none value)
         (doto (init this)
           (as-> v (.putAttachment this MEMOIZED_KEY (assoc m k v))))
         value)))))

(declare -exchange)

(defn- server-url
  "Returns URL for the server running on a random port."
  [^Undertow server, path]
  (let [listener (first (.getListenerInfo server))
        port (.getPort ^InetSocketAddress (.getAddress ^Undertow$ListenerInfo listener))]
    (str
      "http://localhost:" port path)))

(defn- http-post
  [url body]
  (println url)
  (.body (.send (HttpClient/newHttpClient)
                (-> (HttpRequest/newBuilder)
                    (.header "X-Test" "1")
                    (.header "X-Test" "2")
                    (.uri (URI. url))
                    (.POST (HttpRequest$BodyPublishers/ofString body))
                    (.build))
                (HttpResponse$BodyHandlers/ofString))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Start web server and execute HTTP request

(let [server (-> (Undertow/builder)
                 (.addHttpListener 0 "localhost")
                 (.setServerOption UndertowOptions/ENABLE_HTTP2 true)
                 (.setHandler (BlockingHandler.
                                (reify HttpHandler
                                  (handleRequest [_ exchange]
                                    ;; save exchange for the REPL
                                    (def -exchange exchange)
                                    ;; consume request body in handler
                                    (doto exchange (request/body-bytes!))
                                    (-> (.getResponseSender exchange)
                                        (.send "OK"))))))
                 (.build))]
  (.start server)
  (http-post (server-url server "/test-path?a=1&b=2")
             "test-body")
  (.stop server))

(comment
  (request/method -exchange)
  (request/scheme -exchange)
  (request/host -exchange)
  (request/port -exchange)
  (request/path -exchange)
  (request/query-string -exchange)
  (request/protocol -exchange)

  (request/header -exchange "X-Test")
  (request/header* -exchange "X-Test")
  (request/header -exchange "Missing")
  (let [[_ b] (request/header* -exchange "X-Test")] b)
  (last (request/header* -exchange "X-Test"))
  (count (request/header* -exchange "X-Test"))
  (request/header-names -exchange)
  (last (request/header-names -exchange))
  (count (request/header-names -exchange))

  (String. (request/body-bytes! -exchange))
  (request/remote-addr -exchange)
  (request/remote-host -exchange)
  (request/memoized -exchange)

  (request/inspect -exchange)

  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
