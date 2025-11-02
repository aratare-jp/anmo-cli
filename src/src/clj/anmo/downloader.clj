(ns anmo.downloader
  (:require [aleph.http :as http]
            [anmo.info-fetcher :as aif]
            [clj-commons.byte-streams :as bs]
            [clojure.java.io :as io]
            [manifold.deferred :as d])
  (:import (java.io File InputStream OutputStream)
           (java.util.concurrent ExecutorCompletionService Executors)
           (me.tongfei.progressbar ProgressBar)
           (org.apache.commons.io FileUtils)))

(defn download-file
  [download-dir {:keys [mod-id mod-name mod-file-name mod-file-size mod-version mod-download-url] :as mod-info}]
  (try
    (let [mod-dir (io/file download-dir (str mod-id) mod-version)
          ^File mod-file (io/file mod-dir mod-file-name)
          bytes-to-read 4096
          buffer-array (byte-array bytes-to-read)]
      (if (and (.exists mod-file) (= mod-file-name (.getName mod-file)) (= mod-file-size (.length mod-file)))
        {:status :already-downloaded}
        (do
          (when (not (.exists mod-dir))
            (.mkdirs mod-dir))
          (with-open [progress-bar (new ProgressBar mod-name mod-file-size)
                      ^InputStream in @(d/chain
                                         (http/get mod-download-url {:retry-handler (fn [ex try-count http-context]
                                                                                      (println "Got:" ex)
                                                                                      (< try-count 3))})
                                         :body
                                         bs/to-input-stream)
                      ^OutputStream out (io/output-stream mod-file)]
            (loop [bytes-read (.read in buffer-array)]
              (when (not= -1 bytes-read)
                (.stepBy progress-bar bytes-read)
                (.write out buffer-array 0 bytes-read)
                (recur (.read in buffer-array)))))
          {:status :downloaded})))
    (catch Exception e
      (.printStackTrace e)
      (FileUtils/deleteDirectory (io/file download-dir (str mod-id)))
      {:status :error :cause e})))

(defn download-mods
  ([mods-conf mods-info]
   (let [chunk-size 5
         inner-thread-pool (Executors/newCachedThreadPool)
         thread-pool (new ExecutorCompletionService inner-thread-pool)]
     (try
       (doseq [[idx chunk] (map-indexed vector (partition chunk-size chunk-size [] mods-info))]
         (println "==========" "Downloading batch" (inc idx) "==========")
         (download-mods mods-conf (into {} chunk) thread-pool))
       (finally
         (.shutdownNow inner-thread-pool)))))
  ([{:keys [download-dir] :as mods-conf} mods-info thread-pool]
   (let [download-fn (fn [[mod-id mod-info]]
                       (fn [] {:mod-id mod-id
                               :result (download-file download-dir mod-info)}))
         download-tasks (map download-fn mods-info)]
     (try
       (doseq [^Callable task download-tasks]
         (.submit thread-pool task))
       (doseq [x (range (count download-tasks))]
         (let [task-result (-> thread-pool .take .get)
               mod-id (:mod-id task-result)
               task-status (get-in task-result [:result :status])
               mod-name (get-in mods-info [mod-id :mod-name])
               mod-version (get-in mods-info [mod-id :mod-version])]
           (condp = task-status
             :already-downloaded (println "(" (inc x) "/" (count download-tasks) ")" "(" mod-id ")" mod-name "(" mod-version ")" "already downloaded")
             :error (println "(" (inc x) "/" (count download-tasks) ")" mod-name "errored")
             "")))
       (catch Exception e
         (.printStackTrace e)
         (throw (ex-info "Exception encountered when downloading mods" e)))
       ))))

(defn handle
  [{:keys [download-dir] :as mods-conf} mods-list]
  (let [download-dir-file (io/file download-dir)]
    (when (not (.exists download-dir-file))
      (.mkdir download-dir-file)))
  (let [mods-info (aif/fetch-mod-infos mods-conf mods-list)
        local-download-mods (aif/fetch-local-download-mod-infos mods-conf)
        ;; Filter out all new mods that are not already in local. 
        target-mods (reduce
                      (fn [acc [mod-id mod-data]]
                        (if (not= (get-in local-download-mods [mod-id :mod-version]) (:mod-version mod-data))
                          (assoc acc mod-id mod-data)
                          acc))
                      {}
                      mods-info)]
    (if (empty? target-mods)
      (println "No new mods detected.")
      (download-mods mods-conf target-mods))))

(comment
  (anmo.config/mods-conf)
  (anmo.config/mods-list)
  (partition 3 3 [] (range 10))
  (for [chunk (partition 2 2 [] {:a 1 :b 2 :c 3 :d 4 :e 5})]
    (into {} chunk))
  (into {} (take 1 {:a 1 :b 2}))
  (count (range 0 10000 4096))
  (/ 10000 4096)
  (quot 10000 4096)
  (mod 10000 4096)
  (handle
    (anmo.config/mods-conf)
    (anmo.config/mods-list))

  (require '[anmo.config])

  (handle
    (anmo.config/mods-conf)
    (anmo.config/mods-list))
  (mod 2277376 4096)
  )