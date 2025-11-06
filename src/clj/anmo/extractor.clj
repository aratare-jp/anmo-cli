(ns anmo.extractor
  (:require [anmo.info-fetcher :as aif]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [clojure.string :as string])
  (:import (java.io File)
           (java.nio.charset StandardCharsets)
           (java.nio.file FileVisitResult Files SimpleFileVisitor)
           (java.nio.file.attribute FileAttribute)
           (java.util.concurrent ExecutorCompletionService Executors)
           (java.util.zip ZipInputStream)
           (me.tongfei.progressbar ProgressBar)
           (org.apache.commons.io FileUtils)))


(defn is-mod-dir?
  "Check if this is the mod dir."
  [root-dir]
  (let [root-dir (if (string? root-dir) (io/file root-dir) root-dir)
        root-dir-files (.listFiles root-dir)]
    (or
      (some #(and (not (.isDirectory %)) (= "modinfo.json" (string/lower-case (.getName %)))) root-dir-files)
      (some #(and (.isDirectory %) (= "data" (string/lower-case (.getName %)))) root-dir-files))))

(defn recursive-mod-lookup
  "Recursively check for the mods within root dir."
  ([root-dir]
   (recursive-mod-lookup (if (string? root-dir) (io/file root-dir) root-dir) 0 []))
  ([root-dir depth acc]
   (if (is-mod-dir? root-dir)
     (conj acc root-dir)
     (if (= depth 5)
       acc
       (reduce
         (fn [acc curr]
           (if (is-mod-dir? curr)
             (conj acc curr)
             (recursive-mod-lookup curr (inc depth) acc)))
         acc
         (filter #(.isDirectory %) (.listFiles root-dir)))))))

(defn extract-helper
  "Extract the given zip file to the destination directory."
  ([latest-zip-file dest-dir mod-name mod-file-size]
   (with-open [progress-bar (doto (new ProgressBar mod-name mod-file-size) (.maxHint -1))]
     (try
       (extract-helper latest-zip-file dest-dir mod-name mod-file-size StandardCharsets/UTF_8 progress-bar)
       (catch Exception e
         (.reset progress-bar)
         ()
         (extract-helper latest-zip-file dest-dir mod-name mod-file-size StandardCharsets/ISO_8859_1 progress-bar)))))
  ([latest-zip-file dest-dir mod-name mod-file-size charset progress-bar]
   (with-open [src-is (new ZipInputStream (io/input-stream latest-zip-file) charset)]
     (loop [zip-entries []]
       (let [zip-entry (.getNextEntry src-is)]
         (if-not zip-entry
           zip-entries
           (let [out-file (io/file dest-dir (.getName zip-entry))
                 bytes-read-size 4096
                 bytes-array (byte-array bytes-read-size)]
             (if (.isDirectory zip-entry)
               (do
                 (when (.exists out-file)
                   (FileUtils/deleteDirectory out-file))
                 (.mkdirs out-file))
               (let [parent (.getParentFile out-file)]
                 (.mkdirs parent)
                 (when (.exists out-file)
                   (FileUtils/delete out-file))
                 (with-open [file-os (io/output-stream out-file)]
                   (loop [bytes-read (.read src-is bytes-array)]
                     (when (not= -1 bytes-read)
                       (.stepBy progress-bar bytes-read)
                       (.write file-os bytes-array 0 bytes-read)
                       (recur (.read src-is bytes-array)))))))
             (recur (conj zip-entries (.getName zip-entry))))))))))

(defn get-root-dir
  "Given a path file foo/bar/baz, return foo as the root dir of the path."
  [path]
  (let [f (if (string? path) (io/file path) path)
        p (.toPath f)]
    (.getName p 0)))

(defn extract-file
  "Extract the source mod directory to the destination directory, and then assign a new file anmo.edn with the given extra info."
  [source-mod-dir dest-dir mod-id mod-info]
  (try
    (let [zip-files (filter #(and (.isFile %) (string/ends-with? (.getName %) ".zip")) (.listFiles source-mod-dir))
          latest-zip-file (last (sort-by #(.lastModified %) zip-files))
          mod-name (:mod-name mod-info)
          mod-file-size (:mod-file-size mod-info)
          extra-info {:mod-id mod-id :mod-version (:mod-version mod-info)}]
      (when-not (.exists dest-dir)
        (.mkdirs dest-dir))
      (let [zip-entries (extract-helper latest-zip-file dest-dir mod-name mod-file-size)
            ;; The root mod dirs within the zip folder. Sometimes they can be nested. This should show all such dirs.
            mod-root-dirs (->> zip-entries
                               (map get-root-dir)
                               (into #{})
                               (map #(.toFile %))
                               (map #(io/file dest-dir %))
                               (map recursive-mod-lookup)
                               (apply concat))]
        (doseq [mod-root-dir mod-root-dirs]
          (let [extra-info-file (io/file mod-root-dir "anmo.edn")]
            (when (.exists extra-info-file)
              (.delete extra-info-file))
            (with-open [writer (io/writer extra-info-file)]
              (pprint/pprint extra-info writer))))
        {:status :extracted}))
    (catch Exception e
      (.printStackTrace e)
      {:status :error :cause e})))

(defn extract-mods
  ([mods-conf mods-info]
   (let [chunk-size 5
         inner-thread-pool (Executors/newFixedThreadPool 5)
         thread-pool (new ExecutorCompletionService inner-thread-pool)]
     (try
       (doseq [[idx chunk] (map-indexed vector (partition chunk-size chunk-size [] mods-info))]
         (println "==========" "Extracting batch" (inc idx) "==========")
         (extract-mods mods-conf (into {} chunk) thread-pool))
       (finally
         (.shutdownNow inner-thread-pool)))))
  ([{:keys [download-dir mods-dir] :as mods-conf} mods-info thread-pool]
   (let [tmp-dest-dir (.toFile (Files/createTempDirectory "anno-cli" (into-array FileAttribute [])))
         mods-dir (io/file mods-dir)
         ;; Extract to tmp dir 
         extract-fn (fn [[mod-id {:keys [mod-version mod-name] :as mod-info}]]
                      (let [download-mod-dir (io/file download-dir (name mod-id) mod-version)]
                        (fn [] {:mod-id mod-id
                                :result (extract-file download-mod-dir tmp-dest-dir mod-id mod-info)})))
         download-tasks (map extract-fn mods-info)]
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
             :error (println "(" (inc x) "/" (count download-tasks) ")" mod-name "errored")
             "")))
       (catch Exception e
         (.printStackTrace e)
         (throw (ex-info "Exception encountered when downloading mods" e))))

     ;; Move extracted mods from the tmp dir to the new dir
     (doseq [tmp-mod-dir (recursive-mod-lookup tmp-dest-dir)]
       (let [extra-info (-> tmp-mod-dir (io/file "anmo.edn") slurp edn/read-string)
             mod-id (:mod-id extra-info)
             is-disabled? (get-in mods-info [mod-id :is-disabled?])
             mod-name (.getName tmp-mod-dir)
             original-dest-dir (io/file mods-dir mod-name)
             dest-dir (io/file mods-dir (str (if is-disabled? "-" "") mod-name))
             ]
         (FileUtils/moveDirectoryToDirectory tmp-mod-dir mods-dir true)
         (when is-disabled?
           (.renameTo original-dest-dir dest-dir)))))))

(defn find-imya-files
  "Recursively walk root dir to find all IMYA files, i.e. files that have imyatweak in their names."
  [root-dir]
  (let [results (atom [])
        visitor (proxy
                  [SimpleFileVisitor]
                  []
                  (visitFile [file attrs]
                    (when (string/includes? (.getFileName file) "imyatweak")
                      (swap! results conj (.toString file)))
                    FileVisitResult/CONTINUE))]
    (Files/walkFileTree (.toPath root-dir) visitor)
    @results))

(defn find-mod-sub-path
  [mods-dir mod-path]
  (let [start-index (string/index-of mod-path mods-dir)]
    (.substring mod-path (+ start-index (count mods-dir) 1))))

(defn update-mods
  "Update the target mods while preserving IMYA files."
  [{:keys [mods-dir] :as mods-conf} target-mods]
  ;; Remove the current local mods
  (let [root-temp-dir (.toFile (Files/createTempDirectory "anmo-cli" (into-array FileAttribute [])))
        imya-files (atom [])]
    (doseq [[mod-id {:keys [mod-path] :as local-mod}] target-mods]
      (when mod-path
        (let [mod-file (io/file mod-path)]
          (when (.exists mod-file)
            ;; Save the IMYA files into some tmp place
            (doseq [imya-file (find-imya-files mod-file)]
              (let [src-file (io/file imya-file)
                    subpath (find-mod-sub-path (:mods-dir mods-conf) imya-file)
                    dest-file (io/file root-temp-dir subpath)]
                (FileUtils/copyFile src-file dest-file)
                (swap! imya-files conj dest-file)))
            (FileUtils/deleteDirectory mod-file)))))

    ;; Extract the new mods into the mods dir
    (extract-mods mods-conf target-mods)

    ;; Move back the IMYA files
    (doseq [src-file @imya-files]
      (let [subpath (find-mod-sub-path (.getAbsolutePath root-temp-dir) (.getAbsolutePath src-file))
            dest-file (io/file mods-dir subpath)]
        (FileUtils/copyFile ^File src-file dest-file)))))

(defn find-new-mods
  "Filter out all new mods that are not already in local."
  [mods-conf mods-list]
  (let [mods-info (aif/fetch-mods-info mods-conf mods-list)
        local-mods (aif/fetch-local-mods-info mods-conf)]
    (reduce
      (fn [acc [mod-id mod-data]]
        (if (not= (get-in local-mods [mod-id :mod-version]) (:mod-version mod-data))
          (assoc acc mod-id (merge (get local-mods mod-id) mod-data))
          acc))
      {}
      mods-info)))

(defn handle
  [{:keys [mods-dir] :as mods-conf} mods-list]
  (let [mods-dir (io/file mods-dir)]
    (when-not (.exists mods-dir)
      (.mkdirs mods-dir))
    (let [target-mods (find-new-mods mods-conf mods-list)]
      (if (empty? target-mods)
        (println "No new mods detected.")
        (update-mods mods-conf target-mods)))))

(comment
  (doseq [path (find-imya-files (io/file "D:\\SteamLibrary\\steamapps\\common\\Anno 1800\\mods\\[Cheat] Combined Influence Mod for Residences (Taludas)"))]
    (println (-> path io/file .exists)))
  (find-mod-sub-path (anmo.config/mods-conf) "D:\\SteamLibrary\\steamapps\\common\\Anno 1800\\mods\\[Cheat] Combined Influence Mod for Residences (Taludas)")
  (update-mods
    {:mods-dir     "C:\\Users\\suppaionigiri\\Downloads\\anno-1800-mods-extracted"
     :download-dir "C:\\Users\\suppaionigiri\\Downloads\\anno-1800-mods"}
    {:3227838 {:mod-path      "C:\\Users\\suppaionigiri\\Downloads\\anno-1800-mods-extracted\\[Cheat] Combined Influence Mod for Residences (Taludas)"
               :mod-id        3227838
               :mod-name      "[Cheat] Combined Influence Mod for Residences (Taludas)"
               :mod-version   "v1.0.1"
               :mod-file-size 1000}})
  (clojure.pprint/pprint {:a 1 :b 2})
  (is-mod-dir? "/home/suppaionigiri/Downloads/anno-1800-mods/3210489/1.0.1/[Adjustments] Harbor Blocking")
  (require '[anmo.config])
  (handle
    (anmo.config/mods-conf)
    (anmo.config/mods-list)
    )
  (recursive-mod-lookup (io/file "/tmp/cool"))
  (extract-file
    (io/file "C:/Users/suppaionigiri/Downloads/anno-1800-mods/3229533/v1.6.8")
    (io/file "C:/Users/suppaionigiri/Downloads/anno-1800-mods/3229533/v1.6.8")
    :3229533
    {:mod-id :3229533 :mod-file-size 10000 :mod-version "v1.6.8"})
  (extract-file
    (io/file "C:/Users/suppaionigiri/Downloads/anno-1800-mods/4908997/1.0.20")
    (io/file "C:/Users/suppaionigiri/Downloads/anno-1800-mods/4908997/1.0.20")
    :4908997
    {:mod-id :4908997 :mod-file-size 10000 :mod-version "1.0.20"})
  (recursive-mod-lookup (io/file "C:/Users/suppaionigiri/Downloads/anno-1800-mods/4908997/1.0.20/translation"))
  (if {} true false)
  (is-mod-dir? "/home/suppaionigiri/Downloads/anno-1800-mods-extract/[Gameplay] Commuter pier for Enbesa/data")
  (is-mod-dir? "/home/suppaionigiri/Downloads/anno-1800-mods-extract/[Building] Local Department_Knights Castle (Lion053)")
  )