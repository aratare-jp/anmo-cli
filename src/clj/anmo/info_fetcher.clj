(ns anmo.info-fetcher
  (:require [clj-http.client :as client]
            [cheshire.core :as cc]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.tools.reader.edn :as edn])
  (:import (java.io File FileNotFoundException)))

(defn fetch-mod-infos
  "Fetch info from mod.io and return a map of mod IDs and mod infos"
  [{:keys [base-url game-id api-key] :as mods-conf} mods-list]
  (let [mod-ids (mapv name mods-list)
        chunk-size 100
        mods-ids-chunks (partition chunk-size chunk-size [] mod-ids)]
    (reduce
      (fn [acc chunked-mod-ids]
        (let [response (-> (str base-url "/games/" game-id "/mods")
                           (client/get {:query-params {"api_key" api-key "id-in" (string/join "," chunked-mod-ids)}}))
              response-status (:status response)]
          (if (= 200 response-status)
            (let [response-data (-> response :body (cc/parse-string true) :data)]
              (reduce (fn [acc mod-info]
                        (let [mod-name (:name mod-info)
                              mod-name-id (:name_id mod-info)
                              mod-id (get-in mod-info [:modfile :mod_id])
                              mod-file-name (get-in mod-info [:modfile :filename])
                              mod-version (get-in mod-info [:modfile :version])
                              mod-download-url (get-in mod-info [:modfile :download :binary_url])
                              mod-file-size (get-in mod-info [:modfile :filesize])]
                          (assoc acc (keyword (str mod-id)) {:mod-name         mod-name
                                                             :mod-name-id      mod-name-id
                                                             :mod-id           mod-id
                                                             :mod-file-name    mod-file-name
                                                             :mod-file-size    mod-file-size
                                                             :mod-version      mod-version
                                                             :mod-download-url mod-download-url}))
                        ) acc response-data))
            acc
            )))
      {}
      mods-ids-chunks)))


(defn fetch-disabled-mods
  "Scan and get all disabled mods from local mods dir."
  [{:keys [mods-dir] :as mods-conf} mods-data]
  (let [mod-dirs (filter #(.isDirectory %) (.listFiles (io/file mods-dir)))
        disabled-mod-dirs (filter #(string/starts-with? (.getName %) "-") mod-dirs)
        disabled-mod-ids (map #(-> % (io/file "anmo.edn") slurp edn/read-string :mod-id) disabled-mod-dirs)]
    disabled-mod-ids))

(defn fetch-local-mod-infos
  "Scan and get all local mods along with their dir names."
  [{:keys [mods-dir] :as mods-conf}]
  (let [mod-dirs (filter #(.isDirectory %) (.listFiles (io/file mods-dir)))]
    (reduce
      (fn [acc curr]
        (try
          (let [mod-data (-> curr (io/file "anmo.edn") slurp edn/read-string)
                mod-id (:mod-id mod-data)
                mod-version (:mod-version mod-data)
                is-disabled? (string/starts-with? (.getName curr) "-")]
            (assoc acc mod-id {:mod-version mod-version :is-disabled? is-disabled? :mod-path (.getAbsolutePath curr)}))
          (catch FileNotFoundException e
            acc)))
      {}
      mod-dirs)))

(defn fetch-local-download-mod-infos
  "Scan and get all local downloaded mods (not extracted) along with their dir names."
  [{:keys [download-dir] :as mods-conf}]
  (let [download-mod-dirs (filter #(.isDirectory %) (.listFiles (io/file download-dir)))]
    (reduce
      (fn [acc mod-file]
        (let [mod-id (-> mod-file (.getName))
              mod-versions (filter #(.isDirectory %) (.listFiles mod-file))
              latest-mod-version (->> mod-versions (map #(.getName %)) (sort-by str) last)]
          (assoc acc (keyword mod-id) {:mod-version latest-mod-version})))
      {}
      download-mod-dirs)))


(comment
  (fetch-mod-infos mods-conf [3210573])
  (fetch-mod-infos
    {:game-id      4169
     :base-url     "https://u-1792907.modapi.io/v1"
     :api-key      "38f198f4691abc49b6df1b128b84fe13"
     :download-dir "/home/suppaionigiri/Downloads/anno-1800-mods"
     :mods-dir     "/mnt/data/SteamLibrary/steamapps/common/anno-1800/mods"}
    {:3031538 {:folder-name  "[Gameplay] Extended Agriculture Modules (Kurila)",
               :version      "1.7.8",
               :last-updated "2025-09-08T01:37:14.648406",
               :mod-name     "Extended Agriculture Modules (Kurila)",
               :status       "downloaded",
               :disabled     false},
     :3210445 {:folder-name  "[Addon] Harborlife",
               :version      "1.6.5",
               :last-updated "2025-09-08T01:37:17.210705",
               :mod-name     "Harborlife [Spice it Up]",
               :status       "downloaded",
               :disabled     false},
     :3210489 {:folder-name  "[Adjustments] Harbor Blocking",
               :version      "1.0.1",
               :last-updated "2025-09-08T01:37:18.995282",
               :mod-name     "Harbor Blocking [Spice it Up]",
               :status       "downloaded",
               :disabled     false}})
  (fetch-disabled-mods
    (assoc (anmo.config/mods-conf) :mods-dir "/home/suppaionigiri/Downloads/anno-1800-mods-extract")
    (into {} (take 10 (anmo.config/mods-list))))
  (:3874383 (fetch-local-mod-infos (assoc (anmo.config/mods-conf) :mods-dir "C:/Users/suppaionigiri/Downloads/anno-1800-mods-extract")))
  )