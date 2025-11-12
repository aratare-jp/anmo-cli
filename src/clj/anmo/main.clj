(ns anmo.main
  (:require
    [anmo.adder :as aa]
    [anmo.config :as ac]
    [anmo.downloader :as ad]
    [anmo.extractor :as ax]
    [anmo.scanner :as as]
    [clojure.edn]
    [clojure.string :as string]
    [clojure.tools.cli :as cli])
  (:gen-class))

(def cli-options
  ;; An option with a required argument
  [;; A boolean option defaulting to nil
   ["-h" "--help"]])

(defn usage [options-summary]
  (->> ["CLI tool to manage Anno mods"
        ""
        "Usage: java -jar anmo-cli.jar [options] action"
        ""
        "Options:"
        options-summary
        ""
        "Actions:"
        "  add MOD_ID1 MOD_ID2..."
        "  remove MOD_ID1 MOD_ID2..."
        "  download"
        "  extract"
        "  sync"
        "  scan"]
       (string/join \newline)))

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (string/join \newline errors)))

(defn validate-args
  "Validate command line arguments. Either return a map indicating the program
  should exit (with an error message, and optional ok status), or a map
  indicating the action the program should take and the options provided."
  [args]
  (let [{:keys [options arguments errors summary] :as parsed} (cli/parse-opts args cli-options)]
    (cond
      ; help => exit OK with usage summary
      (:help options)
      {:exit-message (usage summary) :ok? true}
      ; errors => exit with description of errors
      errors
      {:exit-message (error-msg errors)}
      ;; custom validation on arguments
      (and (= 1 (count arguments))
           (#{"scan" "sync" "download" "extract"} (first arguments)))
      {:action (first arguments) :options options}
      (and (< 1 (count arguments))
           (#{"add" "remove"} (first arguments)))
      {:action (first arguments) :arguments (rest arguments) :options options}
      ; failed custom validation => exit with usage summary
      :else
      {:exit-message (usage summary)})))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn -main [& args]
  (let [{:keys [action arguments options exit-message ok?]} (validate-args args)]
    (if exit-message
      (exit (if ok? 0 1) exit-message)
      (let [mods-conf (ac/mods-conf)
            mods-list (ac/mods-list)]
        (case (keyword action)
          :sync (do
                  (ad/handle mods-conf mods-list)
                  (ax/handle mods-conf mods-list))
          :download (ad/handle mods-conf mods-list)
          :extract (ax/handle mods-conf mods-list)
          :scan (as/scan-mods mods-conf mods-list)
          :add (aa/add-mods arguments)
          :remove (aa/remove-mods arguments)
          (throw (new RuntimeException "Unknown mode")))))))

(comment
  (let [arguments ["add" "1234"]]
    (println (count arguments))
    (and (> (count arguments) 1)
         ))
  (validate-args ["add" "1234"])
  (System/getProperty "user.home")
  (clojure.edn/read-string (slurp "/home/suppaionigiri/.config/anmo-cli/mods.edn"))
  (System/getProperty "os.name")
  (clojure.repl/doc case)
  (-main)
  (user/start)
  (-main "sync")
  (-main "add" "4999992" "3277161" "4767273" "5200281")
  (-main "remove" "4767273")
  (-main "scan")
  )