(ns thomasa.morpheus.core
  (:require [clj-kondo.core :as clj-kondo]
            [loom.graph :as graph]
            [loom.io :as lio]
            [loom.derived :as derived]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [java.io File]))

(defn lint-analysis [paths]
  (:analysis
   (clj-kondo/run! {:lint paths
                    :config {:output {:analysis true}}})))

(defn var-deps-graph [analysis]
  (let [nodes (map
               (fn [{:keys [ns name]}] (str ns "/" name))
               (:var-definitions analysis))
        edges (map
               (fn [{:keys [name to from from-var]}]
                 [(str from "/" from-var) (str to "/" name)])
               (:var-usages analysis))]
    (apply graph/digraph (concat nodes edges))))

(defn node-subgraph->file [dir format g node]
  (-> (derived/subgraph-reachable-from g node)
      (lio/render-to-bytes :fmt (keyword format))
      (io/copy
       (File. dir (str (str/replace node "/" ":") "." (name format))))))

(comment
  (let [analysis (lint-analysis "src")
        nodes (map
               (fn [{:keys [ns name]}] (str ns "/" name))
               (:var-definitions analysis))]
    (doseq [node nodes]
      (node-subgraph->file "graphs/" var-deps-graph node))))
