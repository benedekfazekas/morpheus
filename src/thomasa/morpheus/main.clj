(ns thomasa.morpheus.main
  (:require [thomasa.morpheus.core :as m]
            [clojure.tools.cli :as cli]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn -main [& args]
  (let [{:keys [arguments errors] {:keys [dir format]} :options}
        (cli/parse-opts
         args
         [["-d" "--dir DIR" "Directory to save output files to"
           :parse-fn #(.getCanonicalFile (io/file %))
           :validate [#(.exists %)]]
          ["-f" "--format FORMAT" "dot, png, svg"
           :default "dot"
           :validate [#{"dot" "png" "svg"}]]])]
    (if-not errors
      (let [analysis (m/lint-analysis arguments)
            graph    (m/var-deps-graph analysis)
            nodes    (map
                      (fn [{:keys [ns name]}] (str ns "/" name))
                      (:var-definitions analysis))]
        (doseq [node nodes]
          (m/node-subgraph->file dir format graph node)))
      (println
       "Errors occured while parsing command:\n"
       (str/join "\n" errors)))))
