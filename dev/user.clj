(ns user
  (:require (vignette.storage [core :refer (create-image-storage)]
                              [local :as vlocal]
                              [protocols :refer :all]
                              [s3 :as vs3])
            (vignette.api.legacy [routes :as alr]
                                 [test :as t])
            (vignette.http [routes :as r])
            (vignette.util [integration :as itg]
                           [thumbnail :as u]
                           [byte-streams :as bs]
                           [filesystem :as fs])
            (vignette [server :as s]
                      [protocols :refer :all]
                      [media-types :as mt]
                      [system :refer :all])
            [wikia.common.logger :as log]
            [aws.sdk.s3 :as s3]
            [midje.repl :refer :all]
            [clout.core :as c]
            [ring.mock.request :refer :all]
            [cheshire.core :refer :all]
            [clojure.tools.trace :refer :all]
            [clojure.java.io :as io]
            [clojure.tools.namespace.repl :as nrepl]
            [clojure.java.shell :refer (sh)]
            [pantomime.mime :refer [mime-type-of]])
  (:use [environ.core]))

(def sample-original-hash {:wikia "bucket"
                           :top-dir "a"
                           :middle-dir "ab"
                           :request-type :original
                           :original "ropes.jpg"})

(def sample-thumbnail-hash {:wikia "bucket"
                            :top-dir "a"
                            :middle-dir "ab"
                            :request-type :thumbnail
                            :original "ropes.jpg"
                            :mode "resize"
                            :height "10"
                            :width "10"})

(def los  (vlocal/create-local-object-storage itg/integration-path))
(def lis  (create-image-storage los))

(def system-local (create-system lis))

(def s3os  (vs3/create-s3-object-storage vs3/storage-creds))
(def s3s   (create-image-storage s3os "images" "images/thumb"))

(def system-s3 (create-system s3s))

(comment
  (start S 8080)
  (stop S))

(defn reload-repl
  []
  (nrepl/set-refresh-dirs "src")
  (nrepl/refresh)
  (clojure.core/use '[clojure.core])
  (use '[clojure.repl])
  ;(load-file "dev/user.clj")
  )

(defn re-init-dev
  ([port]
   (do
     (stop system-s3)
     (nrepl/refresh)
     (clojure.core/use '[clojure.core])
     (use '[clojure.repl])
     (load-file "dev/user.clj")
     (start system-s3 port)))
  ([]
   (re-init-dev 8080)))

(defn mime-stats [path]
  (defn benchmark [file]
    (let [start (System/nanoTime)]
      (mime-type-of file)
      (- (System/nanoTime) start)))
  (when-let [dir (clojure.java.io/file path)]
    (loop [files (file-seq dir)
           time-total 0
           mime-count 0]
      (if-let [file (first files)]
        (if (not (.isDirectory file))
          (recur (rest files) (+ (benchmark file) time-total) (inc mime-count))
          (recur (rest files) time-total mime-count))
        (println (clojure.string/join "\n" [(str "file count: " mime-count)
                                            (str "total time (ns): " time-total)
                                            (str "average (ns): " (float (/ time-total mime-count)))
                                            (str "average (ms): " (* 0.000001 (/ time-total mime-count)))]))))))
