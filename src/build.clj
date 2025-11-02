(ns build
  (:refer-clojure :exclude [test])
  (:require [clojure.tools.build.api :as b]))

(def lib 'me.suppai/anmo)
(def version "STUB")
(def main 'anmo.main)
(def class-dir "target/classes")

(defn test "Run all the tests." [opts]
  (let [basis    (b/create-basis {:aliases [:test]})
        cmds     (b/java-command
                  {:basis     basis
                   :main      'clojure.main
                   :main-args ["-M" "kaocha.runner"]})
        {:keys [exit]} (b/process cmds)]
    (when-not (zero? exit) (throw (ex-info "Tests failed" {}))))
  opts)

(defn- uber-opts [opts]
   (let [opts (merge
                {:lib lib
                 :version version
                 :main main
                 :basis (b/create-basis {})
                 :class-dir class-dir
                 :src-dirs ["src"]
                 :ns-compile [main]}
                opts)
         lib (:lib opts)
         version (-> opts :version name)]
    (assoc opts :uber-file (format "target/%s-%s.jar" lib version))))

(defn ci "Run the CI pipeline of tests (and build the uberjar)." [opts]
  ;(test opts)
  (b/delete {:path "target"})
  (let [opts (uber-opts opts)]
    (println "\nCopying source...")
    (b/copy-dir {:src-dirs ["resources" "src"] :target-dir class-dir})
    (println (str "\nCompiling " main "..."))
    (b/compile-clj opts)
    (println "\nBuilding JAR...")
    (b/uber opts))
  opts)
