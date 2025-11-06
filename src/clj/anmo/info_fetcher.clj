(ns anmo.info-fetcher
  (:require [aleph.http :as http]
            [anmo.schemas :as as]
            [cheshire.core :refer [parse-string]]
            [clj-commons.byte-streams :as bs]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.tools.reader.edn :as edn]
            [malli.core :as m]
            [manifold.deferred :as d])
  (:import (java.io FileNotFoundException)))

(m/=> fetch-mods-info
  [:=> [:cat as/ModsConf as/ModsList] as/ModsInfo])
(defn fetch-mods-info
  "Fetch info from mod.io and return a map of mod IDs and mod infos"
  [{:keys [base-url game-id api-key] :as mods-conf} mods-list]
  (let [mod-ids (mapv name mods-list)
        chunk-size 100
        mods-ids-chunks (partition chunk-size chunk-size [] mod-ids)]
    (reduce
      (fn [acc chunked-mod-ids]
        (let [response @(-> (str base-url "/games/" game-id "/mods")
                            (http/get {:query-params {"api_key" api-key "id-in" (string/join "," chunked-mod-ids)}}))
              response-status (:status response)]
          (if (= 200 response-status)
            (let [response-data (-> response :body bs/to-string (parse-string true) :data)]
              (reduce
                (fn [acc mod-info]
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
                  )
                acc
                response-data))
            acc)))
      {}
      mods-ids-chunks)))

(m/=> fetch-local-mods-info
  [:=> [:cat as/ModsConf] as/LocalModInfo])
(defn fetch-local-mods-info
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
            (assoc acc mod-id {:mod-version  mod-version
                               :is-disabled? is-disabled?
                               :mod-path     (.getAbsolutePath curr)}))
          (catch FileNotFoundException e
            acc)))
      {}
      mod-dirs)))

(m/=> fetch-local-download-mod-infos
  [:=> [:cat as/ModsConf] as/LocalDownloadModInfo])
(defn fetch-local-download-mod-infos
  "Scan and get all local downloaded mods (not extracted) along with their dir names."
  [{:keys [download-dir] :as mods-conf}]
  (let [download-mod-dirs (filter #(.isDirectory %) (.listFiles (io/file download-dir)))]
    (reduce
      (fn [acc mod-file]
        (let [mod-id (-> mod-file (.getName) keyword)
              mod-versions (filter #(.isDirectory %) (.listFiles mod-file))
              latest-mod-version (->> mod-versions (map #(.getName %)) (sort-by str) last)]
          (assoc acc mod-id {:mod-version latest-mod-version})))
      {}
      download-mod-dirs)))

(comment
  malli.core/predicate-schemas
  (malli.instrument/instrument!)
  (do
    (print "cool\n")
    (flush)
    (Thread/sleep 1000)
    (println "\rASDF")
    )
  (do
    (print "Line 1\nLine 2\nLine 3")
    (flush)
    (Thread/sleep 2000)
    (print "\u001b[2A\rUpdated Line 1\nUpdated Line 2\nUpdated Line 3")
    (flush))

  @(d/chain
     (http/get
       (str (:base-url (anmo.config/mods-conf)) "/games/" (:game-id (anmo.config/mods-conf)) "/mods")
       {:query-params {"api_key" (:api-key (anmo.config/mods-conf)) "id-in" "3210573"}})
     :body
     bs/to-string
     #(parse-string % true))

  (user/start)
  (require '[anmo.config])
  (fetch-mods-info (anmo.config/mods-conf) [:3210573])
  (fetch-local-download-mod-infos (anmo.config/mods-conf))
  (fetch-local-mods-info (assoc (anmo.config/mods-conf) :mods-dir "C:/Users/suppaionigiri/Downloads/anno-1800-mods-extracted")))