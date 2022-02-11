(ns thomasa.morpheus.core-test
  (:require [thomasa.morpheus.core :as m]
            [clojure.edn :as edn]
            [clojure.test :as t]
            [loom.attr :as attr]
            [clojure.java.shell :as sh]))

(def mranderson-analysis (:analysis (edn/read-string (slurp "test-resources/analysis-kondo-2021-09-15.edn"))))
(def re-frame-datatable-analysis (edn/read-string (slurp "test-resources/analysis-re-frame-datatable.edn")))

(t/deftest analysis-test
  (let [morph-temp-dir (doto (java.io.File/createTempFile "morpheus" "") (.delete) (.mkdirs))]
    (println (format "temp dir: %s" (str morph-temp-dir)))
    (sh/with-sh-dir morph-temp-dir
      (sh/sh "git" "clone" "--depth" "1" "git@github.com:benedekfazekas/mranderson.git" "--branch" "v0.5.3" "--single-branch"))
    (let [mranderson-053-analysis (m/lint-analysis [(format "%s/mranderson/src" (str morph-temp-dir))])]
      (t/is (=
             #{:namespace-definitions
               :namespace-usages
               :var-definitions
               :var-usages
               :keywords}
             (set (keys mranderson-053-analysis)))
            "analysis should have the sections morpheus wants to work with")
      (t/is (= 94 (count (:var-definitions mranderson-053-analysis))) "analysis should contain the right amount of var defs")
      (t/is (= 1194 (count (:var-usages mranderson-053-analysis))) "analysis should contain the right amount of var usages"))))

(t/deftest low-level-graph-api-test
  (let [graph (#'m/->graph ["A"] [["A" "B"] ["A" "C"]])]
    (t/is (= #{"A" "B" "C"} (set (m/->nodes graph))) "not the right nodes found in simple graph")
    (t/is (= #{["A" "B"] ["A" "C"]} (set (m/->edges graph))) "not the right edges found in simple graph")))

(t/deftest var-deps-graph-sanity-check-test
  (let [mranderson-graph (m/var-deps-graph mranderson-analysis)
        nodes (m/->nodes mranderson-graph)
        nodes-set (set nodes)]
    (t/is (= 298 (count nodes)) "Not the expected number of nodes found.")
    (t/is (nodes-set "mranderson.move/replace-in-ns-form") "mranderson var is not node")
    (t/is (nodes-set "rewrite-clj.zip/node") "mranderson dependency var is not node")
    (t/is (nodes-set "clojure.core/let") "clojure.core var is not node")))

(t/deftest var-deps-graph-exclusion-test
  (let [mranderson-graph (m/var-deps-graph mranderson-analysis nil #"clojure.core/.*")
        nodes (m/->nodes mranderson-graph)
        nodes-set (set nodes)]
    (t/is (= 183 (count nodes)) "Not the expected number of nodes found.")
    (t/is (nodes-set "mranderson.move/replace-in-ns-form") "mranderson var is not node")
    (t/is (nodes-set "rewrite-clj.zip/node") "mranderson dependency var is not node")
    (t/is (not (nodes-set "clojure.core/let")) "clojure.core var should be excluded and not a node node")))

(t/deftest var-usages-graph-test
  (let [mranderson-core-str-graph (m/var-usages-graph mranderson-analysis "clojure.core/str")
        nodes (m/->nodes mranderson-core-str-graph)
        edges (m/->edges mranderson-core-str-graph)
        nodes-set (set nodes)]
    (t/is (= 40 (count nodes)))
    (t/is (= 66 (count edges)))
    (t/is (nodes-set "clojure.core/str") "var the graph was built for is not a node")
    (t/is (nodes-set "leiningen.inline-deps/generate-default-project-prefix") "dependent node on 'clojure.core/str' is not a node")
    (t/is (nodes-set "mranderson.move/sym->file") "dependent node on 'clojure.core/str' is not a node")))

(t/deftest var-usages-graph-exclusion-test
  (let [mranderson-core-str-graph (m/var-usages-graph mranderson-analysis "clojure.core/str" #"leiningen.*")
        nodes (m/->nodes mranderson-core-str-graph)
        edges (m/->edges mranderson-core-str-graph)
        nodes-set (set nodes)]
    (t/is (= 38 (count nodes)))
    (t/is (= 63 (count edges)))
    (t/is (nodes-set "clojure.core/str") "var the graph was built for is not a node")
    (t/is (not (nodes-set "leiningen.inline-deps/generate-default-project-prefix")) "all packages starting with 'leiningen' should be excluded")
    (t/is (nodes-set "mranderson.move/sym->file") "dependent node on 'clojure.core/str' is not a node")))

(t/deftest filename-test
  (t/is (= "foo.bar__some-var.svg" (m/filename "foo.bar/some-var" :svg))))

(t/deftest path->subgraph-test
  (let [subgraph (m/path->subgraph
                  (m/var-deps-graph mranderson-analysis)
                  ["mranderson.move/replace-in-import"
                   "mranderson.move/replace-in-import*"
                   "mranderson.move/java-style-prefix?"
                   "mranderson.move/java-package"])]
    (t/is (= #{"mranderson.move/java-package" "clojure.core/name" "clojure.string/replace"} (set (m/->nodes subgraph))) "not the right nodes found after navigating to subgraph by path")
    (t/is (= #{["mranderson.move/java-package" "clojure.core/name"]
              ["mranderson.move/java-package" "clojure.string/replace"]}
             (set (m/->edges subgraph)))
          "not the right edges found after navigating to subgraph by path")))

(t/deftest node-add-ref-to-comp-graph-test
  (let [subgraph (m/path->subgraph
                  (m/var-deps-graph mranderson-analysis)
                  ["mranderson.move/replace-in-import"
                   "mranderson.move/replace-in-import*"])
        subgraph-with-refs (m/node-add-ref-to-comp-graph subgraph (m/->nodes subgraph) :svg "-usgs")]
    (t/is (= "./mranderson.move__java-package-usgs.svg" (attr/attr subgraph-with-refs "mranderson.move/java-package" :URL)) "URL ref is not generated properly for mranderson var")
    (t/is (= "./clojure.core__name-usgs.svg" (attr/attr subgraph-with-refs "clojure.core/name" :URL))  "URL ref is not generated properly for clojure.core var")
    (t/is (= "./mranderson.move__replace-in-import*-usgs.svg" (attr/attr subgraph-with-refs "mranderson.move/replace-in-import*" :URL)) "URL ref is not generated properly for var with symbol in its name")
    (t/is (= "./mranderson.move__java-style-prefix%3F-usgs.svg" (attr/attr subgraph-with-refs "mranderson.move/java-style-prefix?" :URL))  "URL ref is not generated properly for var with symbol that needs to be escaped in its name")))

(t/deftest edge-add-ref-to-subgraph
  (let [subgraph  (m/path->subgraph
                   (m/var-deps-graph mranderson-analysis)
                   ["mranderson.move/replace-in-import"
                    "mranderson.move/replace-in-import*"])
        subgraph-with-refs (m/edge-add-ref-to-subgraph subgraph (m/->nodes subgraph) :svg)]
    (t/is (= "./mranderson.move__java-package.svg" (attr/attr subgraph-with-refs ["mranderson.move/->new-import-node" "mranderson.move/java-package"] :URL)) "URL ref is for edge is not pointing where the edge is pointing")))

(t/deftest var-deps-graph-re-frame-test
  (let [re-frame-datatable-init-db-graph (m/var-deps-graph re-frame-datatable-analysis "re-frame-datatable-example.events/initialize-db" #"clojure.core/.*|cljs\..*|:clj-kondo/unknown-namespace/.*")
        nodes (m/->nodes re-frame-datatable-init-db-graph)]
    (t/is (= 4 (count nodes)) "could not find all dependencies of re-frame-datatable-example.events/initialize-db")
    (t/is (= #{"re-frame-datatable-example.events/initialize-db"
              "re-frame-datatable-example.db/default-db"
              "re-frame-datatable-example.model/sample-inbox"
              "re-frame-datatable-example.model/labels"}
             (set nodes))
          "could not find the right dependencies of re-frame-datatable-example.events/initialize-db")
    (t/is (= #{["re-frame-datatable-example.events/initialize-db"
                "re-frame-datatable-example.db/default-db"]
              ["re-frame-datatable-example.db/default-db"
               "re-frame-datatable-example.model/sample-inbox"]
              ["re-frame-datatable-example.db/default-db"
               "re-frame-datatable-example.model/labels"]}
             (set (m/->edges re-frame-datatable-init-db-graph)))
          "could not find all edges on the dep graph for re-frame-datatable-example.events/initialize-db")))

(t/deftest var-usages-re-frame-test
  (let [re-frame-datatable-active-label-graph (m/var-usages-graph re-frame-datatable-analysis "re-frame-datatable-example.subs/active-label" #"clojure.core/.*|cljs\..*|:clj-kondo/unknown-namespace/.*")
        nodes (m/->nodes re-frame-datatable-active-label-graph)]
    (t/is (= 3 (count nodes)) "could not find all usages nodes for re-frame-datatable-example.subs/active-label")
    (t/is (= #{"re-frame-datatable-example.subs/active-label"
              "re-frame-datatable-example.views/main-panel"
              "re-frame-datatable-example.subs/threads-digest"}
             (set nodes))
          "could not find the right usages nodes for re-frame-datatable-example.subs/active-label")
    (t/is (= #{["re-frame-datatable-example.views/main-panel"
                "re-frame-datatable-example.subs/active-label"]
              ["re-frame-datatable-example.subs/threads-digest"
               "re-frame-datatable-example.subs/active-label"]}
             (set (m/->edges re-frame-datatable-active-label-graph))))))
