(ns thomasa.morpheus.core
  (:require [clj-kondo.core :as clj-kondo]
            [loom.graph :as graph]
            [loom.io :as lio]
            [loom.derived :as derived]
            [clojure.java.io :as io]
            [clojure.core.protocols :as p]
            [clojure.datafy :as datafy])
  (:import [java.io File]))

(declare datafy-var-deps-graph)

(defn lint-analysis [paths]
  (:analysis
   (clj-kondo/run! {:lint paths
                    :config {:output {:analysis true}}})))

(defn- ->graph
  ([graph]
   (with-meta
     graph
     {`p/datafy datafy-var-deps-graph}))
  ([nodes edges]
   (->graph (apply graph/digraph (concat nodes edges)))))

(defn- datafy-var-deps-graph [g]
  (with-meta
    {:nodes (graph/nodes g)
     :edges (graph/edges g)}
    {`p/nav
     (fn [_graph _k node]
       (->graph (derived/subgraph-reachable-from g node)))
     `datafy/obj g
     `datafy/class (class g)}))

(defn var-deps-graph [analysis]
  (let [nodes (map
               (fn [{:keys [ns name]}] (str ns "/" name))
               (:var-definitions analysis))
        edges (map
               (fn [{:keys [name to from from-var]}]
                 [(str from "/" from-var) (str to "/" name)])
               (:var-usages analysis))]
    (->graph nodes edges)))

(defn graph->file [dir filename format g]
  (io/copy
   (lio/render-to-bytes g :fmt (keyword format))
   (File. dir (str filename "." (name format)))))

(comment
  (let [analysis (lint-analysis "src")
        nodes (map
               (fn [{:keys [ns name]}] (str ns "/" name))
               (:var-definitions analysis))]
    (doseq [node nodes]
      (graph->file "graphs/" var-deps-graph node))))
