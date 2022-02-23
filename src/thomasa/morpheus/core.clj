(ns thomasa.morpheus.core
  (:require [clj-kondo.core :as clj-kondo]
            [loom.graph :as graph]
            [loom.attr :as attr]
            [loom.io :as lio]
            [loom.derived :as derived]
            [clojure.java.io :as io]
            [clojure.core.protocols :as p]
            [clojure.datafy :as datafy]
            [clojure.string :as str])
  (:import [java.io File]
           [java.net URLEncoder]))

(declare datafy-graph)

(defn filename
  "Generate filename based on `node` and `format`.

  The `/` separator between namespace and var name symbol is replaced in `node`.
  `format` gets to be a file extension."
  [node format]
  (str (str/replace node "/" "__") "." (name format)))

;; analysis
(defn lint-analysis [paths]
  (:analysis
   (clj-kondo/run! {:lint paths
                    :config {:output {:analysis {:keywords true
                                                 :context [:re-frame.core]}}}})))

(defn ->vars
  [analysis exclude-regexp]
  (->> (:keywords analysis)
       (filter #(get-in % [:context :re-frame.core :id]))
       (concat (:var-definitions analysis))
       (map (fn [{:keys [ns from name]}] (str (or ns from) "/" name)))
       (remove #(and exclude-regexp (re-matches exclude-regexp %)))))

(defn ->re-frame-usages [kw-anal id->kw]
  (let [name->kw (into {} (map (juxt :name identity) (vals id->kw)))]
    (->> (filter (fn [kw] (some #{:subscription-ref :event-ref :cofx-ref} (keys (get-in kw [:context :re-frame.core])))) kw-anal)
         (map (fn [{:keys [from from-var ns name context]}]
                (let [from-kw (id->kw (get-in context [:re-frame.core :in-id]))]
                  [(str from "/" (or from-var (:name from-kw)))
                   (str (or ns (:from (name->kw name))) "/" name)]))))))

(defn ->usages
  [analysis exclude-regexp]
  (let [kw-anal (:keywords analysis)
        id->kw (into {} (map (juxt (comp :id :re-frame.core :context) identity) (filter #(get-in % [:context :re-frame.core :id]) kw-anal)))]
    (->> (:var-usages analysis)
         (map
          (fn [{:keys [name to from from-var context] :as usage}]
            [(str from "/" (or from-var (:name (id->kw (get-in context [:re-frame.core :in-id])))))
             (str to "/" name)]))
         (concat (->re-frame-usages kw-anal id->kw))
         (remove
          (fn [[from to]]
            (and exclude-regexp
                 (or (re-matches exclude-regexp from) (re-matches exclude-regexp to))))))))

;; graph
(defn ->nodes
  "Get `nodes` of `graph`"
  [graph]
  (:nodes (datafy/datafy graph)))

(defn ->edges
  "Get `edges` of `graph`"
  [graph]
  (:edges (datafy/datafy graph)))

(defn- ->graph
  ([graph]
   (with-meta
     graph
     {`p/datafy datafy-graph}))
  ([nodes edges]
   (->graph (apply graph/digraph (concat nodes edges)))))

(defn- datafy-graph [g]
  (with-meta
    {:nodes (graph/nodes g)
     :edges (graph/edges g)}
    {`p/nav
     (fn [_graph _k node]
       (->graph (derived/subgraph-reachable-from g node)))
     `datafy/obj g
     `datafy/class (class g)}))

(defn node->subgraph
  "Navigate to subgraph reachable from `node`.

  `node` needs to be immediate child of the root node of `graph`"
  [graph node]
  (datafy/nav (datafy/datafy graph) nil node))

(defn path->subgraph
  "Navigate to subgraph reachable from `path`.

  `path` is applied from the root node of `graph`"
  [graph path]
  (reduce node->subgraph graph path))

;; specific graphs
(defn var-usages-graph
  "Create a digraph based on `analysis` and a given `var` of the format of \"ns-name/var-name\".

  Digraph models var dependency relations of vars dependending on `var`.
  Nodes are vars depending on `var` and `var` itself. Edges are either pointing to `var` or describing
  dependencies between all the vars depending on `var`.

  `analysis` is of the format generated by `clj-kondo`, currently `:var-definitions` and `:var-usages` are used to derive nodes and edges for the graph."
  ([analysis var]
   (var-usages-graph analysis var nil))
  ([analysis var exclude-regexp]
   (let [usages (->usages analysis exclude-regexp)
         edges-to-var (filter #(= var (last %)) usages)
         dependents (set (map first edges-to-var))
         dependents-edges (filter (every-pred (comp dependents first) (comp dependents last)) usages)
         vars (cons var dependents)
         edges (into edges-to-var dependents-edges)]
     (-> (->graph vars edges)
         (attr/add-attr-to-edges :color "blue" dependents-edges)))))

(defn var-deps-graph
  "Create a digraph based on `analysis`.

  Digraph models var dependency relations where nodes are vars.
  An edge pointing from var A to var B means that var A
  depends on/uses var B.

  `analysis` is of the format generated by `clj-kondo`, currently `:var-definitions` and `:var-usages` are used to derive nodes and edges for the graph."
  ([analysis]
   (var-deps-graph analysis nil nil))
  ([analysis var]
   (var-deps-graph analysis var nil))
  ([analysis var exclude-regexp]
   (let [graph (->graph (->vars analysis exclude-regexp) (->usages analysis exclude-regexp))]
     (if var (node->subgraph graph var) graph))))

;; decorating graphs
(defn node-add-ref-to-comp-graph
  "Adds references to the `graph` in the `:URL` attribute of its `nodes` to the `graph-type` of graph for a given node.

  Can be used to add a link to the complementary graph of a given node in a graph."
  [graph nodes format graph-type]
  (reduce
   (fn [seed node]
     (if ((set (->nodes seed)) node)
       (attr/add-attr-to-nodes seed :URL (str "./" (URLEncoder/encode (filename (str node graph-type) format))) [node])
       seed))
   graph
   nodes))

(defn edge-add-ref-to-subgraph
  "Adds references to the `graph` in the `:URL` attribute of its edges to subgraphs the given edge is pointing to if the given edge
  is in the `nodes` list.

  Can be used to add a link to the arrows navigating to the node it is pointing to."
  [graph nodes format]
  (reduce
   (fn [seed [_ to :as edge]]
     (if ((set nodes) to)
       (attr/add-attr-to-edges seed :URL (str "./" (URLEncoder/encode (filename to format))) [edge])
       seed))
   graph
   (->edges graph)))

;; io
(defn graph->file!
  "Write `graph` into a file of `format` in `dir`.

  Filename is generated based on `node` and `format`, see [[filename]] for details.
  Alternatively you can also provide your preferred graphiz `alg`orithm."
  ([graph dir node format]
   (graph->file! graph "dot" dir node format))
  ([graph alg dir node format]
   (io/copy
    (lio/render-to-bytes graph :alg alg :fmt (keyword format))
    (File. dir (filename node format)))))
