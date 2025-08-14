(ns core
  (:require [clojure-stats.core :refer :all] )
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
