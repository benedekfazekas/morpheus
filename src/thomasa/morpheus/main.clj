(ns thomasa.morpheus.main
  (:require [thomasa.morpheus.core :as m]
            [clojure.tools.cli :as cli]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.set :as set]))

(defn usage [options-summary]
  (str/join
   "\n"
   ["Usage: morpheus -d DIR -f FORMAT [-r VAR] [-e EXCLUDE-REGEXP] [--verbose] [--help] paths"
    options-summary]))

(defn- var-usages->file!
  ([graph var vars format dir]
   (var-usages->file! graph var vars format dir true))
  ([graph var vars format dir add-ref-to-comp?]
   (cond-> graph
     add-ref-to-comp? (m/node-add-ref-to-comp-graph vars format "")
     :always (m/graph->file! "fdp" dir (str var "-usgs") format))))

(defn- var-deps->file!
  [graph var int-vars all-vars format dir]
  (-> (m/node-add-ref-to-comp-graph graph all-vars format "-usgs")
      (m/edge-add-ref-to-subgraph int-vars format)
      (m/graph->file! dir var format)))

(defn- ext-vars-for-var [graph var all-internal-vars]
  (let [all-subgraph-vars (m/->nodes (m/node->subgraph graph var))]
    (set/difference (set all-subgraph-vars)
                    (set (filter (set all-internal-vars) all-subgraph-vars)))))

(defn -main [& args]
  (let [{:keys [arguments errors summary] {:keys [dir format help var exclude-regexp verbose]} :options}
        (cli/parse-opts
         args
         [["-d" "--dir DIR" "Directory to save output files to"
           :parse-fn #(.getCanonicalFile (io/file %))
           :validate [#(.exists %)]]
          ["-f" "--format FORMAT" "dot, png, svg"
           :default "dot"
           :validate [#{"dot" "png" "svg"}]]
          ["-r" "--var VAR" "Variable to generate subgraph view for"]
          ["-e" "--exclude-regexp EXCLUDE-REGEXP" "Regexp to exclude nodes from the graph"
           :parse-fn #(when % (re-pattern %))]
          ["-v" "--verbose"]
          ["-h" "--help"]])]
    (cond
      errors
      (println
       "Errors occured while parsing command:\n"
       (str/join "\n" errors))

      help
      (println (usage summary))

      :else
      (let [analysis (m/lint-analysis arguments)
            graph (m/var-deps-graph analysis exclude-regexp)
            all-internal-vars (m/->vars-and-kw-regs analysis exclude-regexp)
            internal-vars (if var [var] all-internal-vars)
            all-vars (m/->nodes graph)
            ext-vars (if var
                       (ext-vars-for-var graph var all-internal-vars)
                       (set/difference (set all-vars) (set internal-vars)))]
        (doseq [var internal-vars]
          (when verbose
            (println (str "Generating graphs for " var))
            (println (str "  Generating deps graph")))
          (var-deps->file! (m/var-deps-graph analysis var exclude-regexp) var internal-vars all-vars format dir)
          (when verbose
            (println (str "  Generatig usages graph")))
          (var-usages->file! (m/var-usages-graph analysis var exclude-regexp) var internal-vars format dir))
        (doseq [ext-var ext-vars]
          (when verbose
            (println (str "Generatig usages graph for external var " ext-var)))
          (var-usages->file! (m/var-usages-graph analysis ext-var exclude-regexp) ext-var ext-vars format dir false))))))
