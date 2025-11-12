(ns clojure-stats.core
  (:require [edamame.core :as e]
            [clojure.walk :as walk]
            [clojure.tools.cli]
            [clojure-stats.output :as output]
            [io.pedestal.log :as log]
            [clj-uuid :as uuid]
            )
  (:import [java.io File])
  (:gen-class))


(comment (set! *warn-on-reflection* true))

(e/parse-string-all "(+ 1 1) (+ 2 2)")


(def clojure-defaults {:all true
                       :row-key :line
                       :col-key :column
                       :end-location false
                       :location? seq?})

(defn read-file
  "Reads a file"
  [file]
  (-> (slurp file)
      (e/parse-string-all (merge clojure-defaults {:auto-resolve-ns true :fn true :features #{:clj} :read-cond :allow}))))

(defn try-resolve [symbol extra-aliases]
  (let [resolved  (resolve symbol)]
    (or resolved symbol)))


(defn tree-seq-ids
  "Creates a seq including GUIDs and depths.

  Based on tree-seq"
  ([branch? children root]
   (tree-seq-ids branch? children root (uuid/v6) ))
  ([branch? children root count]
   (let [walk (fn walk [depth parent-counter root-counter node]
                (let [old-count (swap! count inc)]
                  (lazy-seq
                    (cons {:id old-count :depth depth :node node :parent-id (when (> depth 1) parent-counter) :root-id root-counter}
                          (when (branch? node)
                            (mapcat #(walk (inc depth) old-count (if root-counter root-counter old-count) %) (children node)))))))]

     (walk 1 (uuid/v6)  nil root))))

(defn eager-tree-seq-ids
  "Creates a coll from a tree, including GUIDs and depths.

  Based on tree-seq"
  ([branch? children root]
   (let [walk (fn walk [depth parent-counter root-counter node]
                (let [old-id (uuid/v6)]
                  (conj
                    (if (branch? node)
                      (mapcat #(walk (inc depth) old-id (if root-counter root-counter old-id) %) (children node))
                      [])
                    {:id old-id :depth depth :node node :parent-id (when (> depth 1) parent-counter) :root-id root-counter})))]

     (walk 1 nil nil root))))

(defn enumerate-symbols
  "Enumerates symbols inside a form"
  [forms]

  (->> (for [form forms]
        (->> (tree-seq seq? identity form)
             (filter symbol?)
             (map #( try-resolve % nil))
             frequencies))
      (reduce #(merge-with + %1 %2))))

(defn classify-symbol
  "Classifies a symbol broadly"
  [sym]
  (let [resolved (try-resolve sym nil)
        resolved (if (instance? java.lang.Class resolved) (symbol (.getName ^java.lang.Class resolved)) resolved)]
    (try
      (let [resolved-ns (namespace (symbol resolved))]
        (cond
        (special-symbol? sym) :special
        (= resolved-ns "clojure.core") :ns/clojure.core
        (not (nil? resolved-ns)) (keyword "ns" resolved-ns )
        :else :other))

      (catch IllegalArgumentException e
        (prn (type resolved) resolved)
        :error))))

(defn classify-symbols [forms]
  (->> (for [form forms]
        (->> (tree-seq seq? identity form)
             (filter symbol?)
             (map classify-symbol)
             frequencies))
      (reduce #(merge-with + %1 %2))))


(defn aggregate-top-level [results]
  (apply merge-with + results))


(defn analyze-forms* [{form :node :as processed-form} file]
  (let [type (cond
               (symbol? form) :symbol
               (list? form) :list
               (var? form) :var
               (coll? form) :coll
               (or (string? form) (number? form) (boolean? form)) :data
               :else :other)
        resolved-symbol (when (symbol? form) (try-resolve form nil))
        #_#_symbol-type (when (symbol? form) (classify-symbol resolved-symbol))
        meta (if (nil? file)
                    (meta form)
                    (merge (meta form) {:file (str file)}))]

    (-> processed-form
        (merge {:type type
                :form form
                :resolved-symbol resolved-symbol
                :meta meta
                :clojure-type (type form)
                #_#_:symbol-type symbol-type })
        (dissoc :node))))

(defn analyze-forms
  ([forms]
   (analyze-forms forms nil))
  ([forms file]
   (apply concat
          (for [form forms]
            (->> (eager-tree-seq-ids sequential? seq form)
                 (map #(analyze-forms* % file)))))))

(defn classify-files
  ([files]
   (into [] (comp
              (map (fn [file]
                     (try
                       [file (read-file file)]
                       (catch Exception e
                         (log/warn :msg "Exception: " :data (ex-data e) :cause (ex-cause e))
                         [file nil]))))
              (mapcat (fn [[file forms]] (analyze-forms forms file))))
         files)))

(defn classify-and-write [ out ^String path]
  (let [files  (->> (file-seq (File. path))
                    (filter #(.endsWith (.getName ^File %) ".clj")))]

    (doseq [file-partition (partition 100 files)]
      (clojure-stats.output/write-records out (classify-files file-partition)))))

(def cli-options
  [["-a" "--analysis" :parse-fn keyword]
   ["-t" "--to FORMAT" "Output format. One of " :parse-fn keyword :default :stdout]
   [nil "--overwrite" "Deletes the database if necessary" :default false]
   ["-o" "--output FILE" "Filename to output to."]
   [nil "--fixed-prefix PREFIX" "Override the prefix"]
   ["-h" "--help"]])

(defn -main [& args]

  (let [{:keys [arguments] {:keys [output to analysis overwrite fixed-prefix]} :options :as parsed} (clojure.tools.cli/parse-opts args cli-options)
        output-file (or output "output.db")
        _ (when overwrite
            (doto (File. ^String output-file)
              (.delete)))
        out (case to
              :stdout ^clojure-stats.output.EDNOut (clojure-stats.output/->EDNOut (or fixed-prefix (first arguments)))
              :duckdb ^clojure-stats.output.DuckDBOut  (clojure-stats.output/->DuckDBOut output-file (or fixed-prefix (first arguments)))
              :duckdb_batch ^clojure-stats.output.DuckDBBatchOut (clojure-stats.output/->DuckDBBatchOut output-file (or fixed-prefix (first arguments))))
        out (if (satisfies? clojure-stats.output/Connect out)
              (clojure-stats.output/connect out)
              out)]

    (doseq [arg arguments]
      (classify-and-write out arg))))
