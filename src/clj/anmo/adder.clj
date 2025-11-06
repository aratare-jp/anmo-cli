(ns anmo.adder
  (:require
    [anmo.config :as ac]
    [anmo.info-fetcher :as aif]
    [clojure.set :refer [intersection difference]]
    [clojure.java.io :as io]
    [clojure.string :as string])
  (:import (org.apache.commons.io FileUtils)))

(defn add-mods
  [new-mod-ids]
  (let [mods-list (mapv name (ac/mods-list))
        new-mod-ids-set (into #{} new-mod-ids)
        mods-list-set (into #{} mods-list)
        existing-mod-ids (intersection new-mod-ids-set mods-list-set)
        new-mod-ids (difference new-mod-ids-set mods-list-set)]
    (when (seq existing-mod-ids)
      (println "Mod IDs" (string/join " " existing-mod-ids) "already in list"))
    (when (seq new-mod-ids)
      (let [new-mods-list (into mods-list new-mod-ids)
            content (string/join "\n" new-mods-list)
            mods-list-file (-> "~/.config/anmo-cli"
                               (string/replace "~" ac/home-dir)
                               (str "/mods-list.txt")
                               io/file)]
        (with-open [writer (io/writer mods-list-file)]
          (.write writer content))
        (println "Mod IDs" (string/join " " new-mod-ids) "added")))))

(defn remove-mods
  [to-be-removed-mod-ids]
  (let [local-mods (aif/fetch-local-mods-info (ac/mods-conf))
        mods-list (mapv name (ac/mods-list))
        to-be-removed-mod-ids-set (into #{} to-be-removed-mod-ids)
        mods-list-set (into #{} mods-list)
        existing-mod-ids (intersection to-be-removed-mod-ids-set mods-list-set)
        non-existing-mod-ids (difference to-be-removed-mod-ids-set mods-list-set)]
    (when (seq non-existing-mod-ids)
      (println "Mod IDs" (string/join " " non-existing-mod-ids) "already not in list"))
    (when (seq existing-mod-ids)
      (let [new-mods-list (remove (into #{} existing-mod-ids) mods-list)
            content (string/join "\n" new-mods-list)
            mods-list-file (-> "~/.config/anmo-cli"
                               (string/replace "~" ac/home-dir)
                               (str "/mods-list.txt")
                               io/file)]
        (doseq [mod-id existing-mod-ids]
          (let [mod-id (keyword mod-id)
                to-be-removed-mod-dir-path (get-in local-mods [mod-id :mod-path])
                to-be-removed-mod-dir (io/file to-be-removed-mod-dir-path)]
            (when (and to-be-removed-mod-dir (.exists to-be-removed-mod-dir))
              (FileUtils/deleteDirectory to-be-removed-mod-dir))
            (with-open [writer (io/writer mods-list-file)]
              (.write writer content))))
        (println "Mod IDs" (string/join " " existing-mod-ids) "removed")))))

(comment
  (into [1 2 3 4] #{4 5 6})
  (require '[clojure.set])
  (clojure.set/difference #{1 2} #{1 2 3})
  (clojure.set/difference #{1 2 3} #{1 2 4 5})
  (count (keys (aif/fetch-local-mods-info (ac/mods-conf))))
  (count (aif/fetch-local-mods-info (ac/mods-conf)))
  (count (ac/mods-list))
  (count (clojure.set/intersection (into #{} (keys (aif/fetch-local-mods-info (ac/mods-conf)))) (into #{} (ac/mods-list))))
  (add-mods ["4999992" "3277161" "4767273" "5200281"])
  (remove-mods ["12345"])
  (remove #{1 2 3} [1 2 3 4 5])
  (when #{} 1)
  (remove-mods ["4767273"])
  )