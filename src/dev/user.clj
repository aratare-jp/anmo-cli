(ns user
  (:require [clojure.tools.namespace.repl :refer [set-refresh-dirs]]
            [malli.dev :as dev]))

(set-refresh-dirs "src/clj" "src/dev")

(defn start
  []
  (dev/start!))

(defn stop
  []
  (dev/stop!))

