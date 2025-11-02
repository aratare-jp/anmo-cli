(ns anmo.utils
  (:require [clj-commons.byte-streams :as bs]
            [aleph.http :as http]
            [cheshire.core :refer [parse-string]]))

(defn http-get
  [url]
  (let [res @(http/get url)]
    (update res :body #(-> % bs/to-string (parse-string true)))))
