(ns core
  (:require [clojure-stats.core :refer :all] 
            [clojure.walk :as walk])
  (:import [java.io File]))

(read-file "../shape-of-clojure/test/clojure/clojurescript/src/main/clojure/cljs/cli.clj")

(enumerate-symbols (read-file "../shape-of-clojure/test/clojure/clojurescript/src/main/clojure/cljs/cli.clj"))
(classify-symbols (read-file "../shape-of-clojure/test/clojure/clojurescript/src/main/clojure/cljs/cli.clj"))


(-> (read-file "../shape-of-clojure/test/clojure/clojurescript/src/main/clojure/cljs/cli.clj")
    first 
    meta)

(-> (read-file "../shape-of-clojure/test/clojure/clojurescript/src/main/clojure/cljs/cli.clj")
    first 
    first
    resolve)


(let [clj-files (->> (file-seq (File. "."))
                     (filter #(.endsWith (.getName %) ".clj"))) 
      counts (for [file clj-files]
               (classify-symbols (read-file file)))]

  (apply merge-with + counts))

(let [clj-files (->> (file-seq (File. "../shape-of-clojure/test/clojure/clojurescript/src"))
                     (filter #(.endsWith (.getName %) ".clj"))) 
      
      counts (for [file clj-files]
               (classify-symbols (read-file file))) ]

  (apply merge-with + counts))

(let [clj-files (->> (file-seq (File. "../shape-of-clojure/test/technomancy/leiningen/src"))
                     (filter #(.endsWith (.getName %) ".clj"))) 
      
      counts (for [file clj-files]
               (classify-symbols (read-file file))) ]

  (apply merge-with + counts))

(classify-files "../shape-of-clojure/test/technomancy/leiningen/src")

(analyze-forms '(test :a))


(map list? '(test :a (test)))

*e
(->> (analyze-forms ['(test ( :a)) '(test :b) '(test :c)])
    (sort-by :id)
    )

(analyze-forms ['(test #(+ 1 2))])

(tree-seq-ids sequential? seq  '(test :a (:b :c)))

(tree-seq sequential? identity [[1 2] 3])

(walk/postwalk
  (fn [m] 
    (if (map? m) 
      (-> m 
          (update :id inc )
          (update :parent-id #(when % (inc %)) ))
      m))
  (tree-seq-ids sequential? identity '(1 2 3 (4 5))))

