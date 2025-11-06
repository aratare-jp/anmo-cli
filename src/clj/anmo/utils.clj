(ns anmo.utils
  (:require [clojure.java.io :as io]
            [malli.core :as m]
            [malli.instrument :as mi])
  (:import (me.tongfei.progressbar ProgressBar ProgressBarStyle)))

(m/=> ensure-file-exists [:=> [:cat :string] :nil])
(defn ensure-file-exists
  "Check if the given file/dir exists, and if not, create it."
  [path-str]
  (let [file (io/file path-str)]))

(m/=> create-progress-bar [:=> [:cat :string pos-int?] :any])
(defn create-progress-bar
  [name initial-size]
  (.build (doto (ProgressBar/builder)
            (.setTaskName name)
            (.setInitialMax initial-size)
            (.setStyle (.build (doto (ProgressBarStyle/builder)
                                 (.leftBracket "[")
                                 (.rightBracket "]")
                                 (.block \=)
                                 (.rightSideFractionSymbol \>)))))))

(mi/instrument!)

(comment
  (user/start)
  (user/stop)
  (ensure-file-exists true))