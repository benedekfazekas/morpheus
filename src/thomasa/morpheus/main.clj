(ns thomasa.morpheus.main
  (:require [thomasa.morpheus.core :as m]
            [clojure.tools.cli :as cli]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.datafy :as datafy]))

(defn usage [options-summary]
  (str/join
   "\n"
   ["Usage: morpheus -d DIR -f FORMAT paths"
    options-summary]))

(defn -main [& args]
  (let [{:keys [arguments errors summary] {:keys [dir format help]} :options}
        (cli/parse-opts
         args
         [["-d" "--dir DIR" "Directory to save output files to"
           :parse-fn #(.getCanonicalFile (io/file %))
           :validate [#(.exists %)]]
          ["-f" "--format FORMAT" "dot, png, svg"
           :default "dot"
           :validate [#{"dot" "png" "svg"}]]
          ["-h" "--help"]])]
    (cond
      errors
      (println
       "Errors occured while parsing command:\n"
       (str/join "\n" errors))

      help
      (println (usage summary))

      :default
      (let [analysis (m/lint-analysis arguments)
            graph    (m/var-deps-graph analysis)
            nodes    (map
                      (fn [{:keys [ns name]}] (str ns "/" name))
                      (:var-definitions analysis))]
        (doseq [node nodes]
          (m/graph->file dir (str/replace node "/" ":") format (datafy/nav (datafy/datafy graph) nil node)))))))
