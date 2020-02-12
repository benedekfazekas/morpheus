(ns thomasa.morpheus.main
  (:require [thomasa.morpheus.core :as m]))

(defn -main [img-dir-prefix & paths]
  (let [analysis (m/lint-analysis paths)
        graph (m/var-deps-graph analysis)
        nodes (map
               (fn [{:keys [ns name]}] (str ns "/" name))
               (:var-definitions analysis))]
    (doseq [node nodes]
      (m/node-subgraph->file img-dir-prefix graph node))))
