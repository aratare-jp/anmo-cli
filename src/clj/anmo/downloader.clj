(ns anmo.downloader
  (:require [aleph.http :as http]
            [anmo.info-fetcher :as aif]
            [anmo.schemas :as as]
            [anmo.utils :as au]
            [clj-commons.byte-streams :as bs]
            [clojure.java.io :as io]
            [malli.core :as m]
            [manifold.deferred :as d])
  (:import (java.io File InputStream OutputStream)
           (java.util.concurrent ExecutorCompletionService Executors)
           (org.apache.commons.io FileUtils)))

(m/=> download-file
  [:=> [:cat :string as/ModsInfo] [:map [:status :keyword]]])
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
          (with-open [progress-bar (au/create-progress-bar mod-name mod-file-size)
                      ^InputStream in @(d/chain
                                         (http/get mod-download-url)
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

(m/=> download-mods
  [:function
   [:=> [:cat as/ModsConf as/ModsInfo] :nil]
   [:=> [:cat as/ModsConf as/ModsInfo as/-ExecutorCompletionService] :nil]])
(defn download-mods
  ([mods-conf mods-info]
   (let [inner-thread-pool (Executors/newVirtualThreadPerTaskExecutor)
         thread-pool (new ExecutorCompletionService inner-thread-pool)]
     (try
       (download-mods mods-conf mods-info thread-pool)
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
         (throw (ex-info "Exception encountered when downloading mods" e)))))))

(m/=> handle
  [:=> [:cat as/ModsConf as/ModsList] :any])
(defn handle
  [{:keys [download-dir] :as mods-conf} mods-list]
  (let [download-dir-file (io/file download-dir)]
    (when (not (.exists download-dir-file))
      (.mkdir download-dir-file)))
  (let [mods-info (aif/fetch-mods-info mods-conf mods-list)
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
  (malli.dev/start!)
  (user/stop)
  (user/start)
  (malli.instrument/instrument!)
  (require '[anmo.config])
  (anmo.config/mods-conf)
  (malli.core/function-schemas)
  (handle
    (-> (anmo.config/mods-conf)
        (assoc :download-dir "C:\\Users\\suppaionigiri\\Downloads\\anno-1800-mods-2")
        (assoc :mods-dir "C:\\Users\\suppaionigiri\\Downloads\\anno-1800-mods-extracted-2")
        )
    (into [] (take 2 (anmo.config/mods-list))))
  (aif/fetch-mods-info
    (-> (anmo.config/mods-conf)
        (assoc :download-dir "C:\\Users\\suppaionigiri\\Downloads\\anno-1800-mods-2")
        (assoc :mods-dir "C:\\Users\\suppaionigiri\\Downloads\\anno-1800-mods-extracted-2")
        )
    (anmo.config/mods-list))
  (aif/fetch-local-download-mod-infos
    (-> (anmo.config/mods-conf)
        (assoc :download-dir "C:\\Users\\suppaionigiri\\Downloads\\anno-1800-mods-2")
        (assoc :mods-dir "C:\\Users\\suppaionigiri\\Downloads\\anno-1800-mods-extracted-2")))

  )