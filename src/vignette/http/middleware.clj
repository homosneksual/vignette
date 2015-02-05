(ns vignette.http.middleware
  (:require [environ.core :refer [env]]
            [compojure.response :refer [render]]
            [ring.util.response :refer [response status charset header get-header]]
            [slingshot.slingshot :refer [try+ throw+]]
            [vignette.util.image-response :refer :all]
            [wikia.common.logger :as log]
            [wikia.common.perfmonitoring.core :as perf])
  (:import [java.net InetAddress]))

(def hostname (.getHostName (InetAddress/getLocalHost)))

(defn exception-catcher
  [handler]
  (fn [request]
    (try+
      (handler request)
      (catch [:type :convert-error] e
        (let [message (:message &throw-context)
              thumb-map (:thumb-map e) ; if present, we'll try to thumbnail the error response
              response-code (or (:response-code e) 500)
              context (assoc (dissoc e :type :thumb-map :response-code) :host hostname)]
          (perf/publish {:convert-error 1})
          (println message ":" (:uri request))
          (log/warn message
                    (merge {:path (:uri request)
                            :query (:query-string request)}
                           context))
          (error-response response-code thumb-map)))
      (catch Exception e
        (println (.getMessage e) ":" (:uri request))
        (perf/publish {:exception-count 1})
        (log/warn (str e) {:path (:uri request)
                           :query (:query-string request)})
        (error-response 500)))))

(defn add-headers
  [handler]
  (fn [request]
    (let [response (handler request)]
      (-> response
          (header "Cache-Control" "public, s-maxage=604800")
          (header "X-Pass-Cache-Control" "public, max-age=31536000")
          (header "Varnish-Logs" "vignette")
          (header "X-Served-By" hostname)
          (header "X-Cache" "ORIGIN")
          (header "X-Cache-Hits" "ORIGIN")
          (header "Connection" "close")))))

(defn request-timer
  [handler]
  (fn [request]
    (perf/publish {:request-count 1})
    (perf/timing :request-time (handler request))))

(defn bad-request-path
  []
  (fn [request]
    (perf/publish {:bad-request-path-count 1})
    (log/warn "bad-request-path" (cond-> {:path (:uri request)}
                                         (get-header request "referer") (assoc :referer (get-header request "referer"))))
    (-> (render "Unrecognized request path!\nSee https://github.com/Wikia/vignette for documentation.\n" request)
        (status 404))))
