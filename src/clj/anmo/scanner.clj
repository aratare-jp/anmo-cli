(ns anmo.scanner
  (:require [anmo.config :as ac]
            [anmo.schemas :as as]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [malli.core :as m]))

(m/=> scan-mods
  [:=> [:cat as/ModsConf as/ModsList] as/ModsInfo])
(defn scan-mods
  "Fetch info from mod.io and return a map of mod IDs and mod infos"
  [{:keys [scan-dir] :as mods-conf} mods-list]
  (let [scan-dir (io/file scan-dir)
        mod-dirs (.listFiles scan-dir)
        mod-ids (filter #(.isDirectory %) mod-dirs)
        mod-ids (mapv #(.getName %) mod-ids)
        content (string/join "\n" mod-ids)
        mods-list-file (-> "~/.config/anmo-cli"
                           (string/replace "~" ac/home-dir)
                           (str "/mods-list.txt")
                           io/file)]
    (when (not (.exists mods-list-file))
      (.createNewFile mods-list-file))
    (with-open [writer (io/writer mods-list-file)]
      (.write writer content))
    (println (count mod-ids) "mods added")))


(comment
  (anmo.config/mods-conf)
  (require '[anmo.config])
  (scan-mods
    (anmo.config/mods-conf)
    (anmo.config/mods-list)))