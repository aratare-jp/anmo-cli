(ns anmo.config
  (:require
    [clojure.java.io :as io]
    [clojure.string :as string]
    [cprop.core :as cc]))

(defn load-config
  [config-file-path]
  (cc/load-config :file config-file-path))

(def home-dir (System/getProperty "user.home"))

(defn mods-conf
  []
  (-> "~/.config/anmo-cli"
      (string/replace "~" home-dir)
      (str "/mods-conf.edn")
      load-config))

(defn mods-list
  []
  (-> "~/.config/anmo-cli"
      (string/replace "~" home-dir)
      (str "/mods-list.txt")
      slurp
      string/split-lines
      (#(mapv keyword %))))

(comment
  (.mkdir (io/file (:download-dir (mods-conf))))
  (mods-list)
  (.contains (mods-list) :5200281)
  (remove #{1} [1 2 3])

  (let [mod-ids (map name (keys (mods-list)))
        string (clojure.string/join "\n" mod-ids)]
    (spit "/home/suppaionigiri/.config/anmo-cli/new-mods.edn" string))
  )