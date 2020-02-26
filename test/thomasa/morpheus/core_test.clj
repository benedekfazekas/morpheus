(ns thomasa.morpheus.core-test
  (:require [thomasa.morpheus.core :as m]
            [clojure.edn :as edn]
            [clojure.test :as t]
            [loom.attr :as attr]))

(def mranderson-analysis (:analysis (edn/read-string (slurp "test-resources/analysis-2020-02-12.edn"))))

(t/deftest low-level-graph-api-test
  (let [graph (#'m/->graph ["A"] [["A" "B"] ["A" "C"]])]
    (t/is (= #{"A" "B" "C"} (set (m/->nodes graph))) "not the right nodes found in simple graph")
    (t/is (= #{["A" "B"] ["A" "C"]} (set (m/->edges graph))) "not the right edges found in simple graph")))

(t/deftest full-graph-sanity-check-test
  (let [mranderson-graph (m/var-deps-graph mranderson-analysis)
        nodes (m/->nodes mranderson-graph)
        nodes-set (set nodes)]
    (t/is (= 295 (count nodes)) "Not the expected number of nodes found.")
    (t/is (nodes-set "mranderson.move/replace-in-ns-form") "mranderson var is not node")
    (t/is (nodes-set "rewrite-clj.zip/node") "mranderson dependency var is not node")
    (t/is (nodes-set "clojure.core/let") "clojure.core var is not node")))

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

(t/deftest add-ref-to-subgraphs-test
  (let [subgraph (m/path->subgraph
                  (m/var-deps-graph mranderson-analysis)
                  ["mranderson.move/replace-in-import"
                   "mranderson.move/replace-in-import*"])
        subgraph-with-refs (m/add-ref-to-subgraphs subgraph (m/->nodes subgraph) :svg)]
    (t/is (= "./mranderson.move__java-package.svg" (attr/attr subgraph-with-refs "mranderson.move/java-package" :URL)) "URL ref is not generated properly for mranderson var")
    (t/is (= "./clojure.core__name.svg" (attr/attr subgraph-with-refs "clojure.core/name" :URL))  "URL ref is not generated properly for clojure.core var")
    (t/is (= "./mranderson.move__replace-in-import*.svg" (attr/attr subgraph-with-refs "mranderson.move/replace-in-import*" :URL)) "URL ref is not generated properly for var with symbol in its name")
    (t/is (= "./mranderson.move__java-style-prefix%3F.svg" (attr/attr subgraph-with-refs "mranderson.move/java-style-prefix?" :URL))  "URL ref is not generated properly for var with symbol that needs to be escaped in its name")))
