(ns clojure-stats.core
  (:require [edamame.core :as e]
            [clojure.walk :as walk]
            [clojure.tools.cli]
            [clojure-stats.output :as output])
  (:import [java.io File]))


(def clojure-defaults {:all true
                       :row-key :line
                       :col-key :column
                       :end-location false
                       :location? seq?})

(defn read-file 
  "Reads a file"
  [file]
  (-> (slurp file)
      (e/parse-string-all (merge clojure-defaults {:auto-resolve-ns true :fn true}) )))

(defn try-resolve [symbol extra-aliases]
  (let [resolved  (resolve symbol)]
    (or resolved symbol)))

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
        resolved (if (instance? java.lang.Class resolved) (symbol (.getName resolved)) resolved)]
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

(defn classify-files [path]
  (let [clj-files (->> (file-seq (File. path))
                       (filter #(.endsWith (.getName %) ".clj"))) 

        counts (->> (for [file clj-files]
                     (try 
                       [(str file)
                        (classify-symbols (read-file file))]
                       (catch Exception e
                         (prn #_e (ex-data e) (ex-cause e))
                         [file nil])))
                   (into {}))]
    counts))

(defn aggregate-top-level [results]
  (apply merge-with + results))



(defn analyze-forms* [form]
  (let [type (cond 
               (symbol? form) :symbol 
               (list? form) :form 
               (var? form) :var 
               (coll? form) :coll
               (or (string? form) (number? form) (boolean? form)) :data 
               :else :other)
        resolved-symbol (when (symbol? form) (try-resolve form nil))
        #_#_symbol-type (when (symbol? form) (classify-symbol resolved-symbol))]

    {:type type
     :form form
     :resolved-symbol resolved-symbol
     :meta (meta form)
     #_#_:symbol-type symbol-type 

     }))

(defn analyze-forms [forms]
  (let [form (first forms)]
    (->> (tree-seq seq? identity form)
       (map analyze-forms*))))


(defn classify-files2 [path]

  (println (file-seq (File. path)))
  (->> (file-seq (File. path))
       (filter #(.endsWith (.getName %) ".clj"))
       (map read-file)
       
       (mapcat analyze-forms )))

(classify-files2 "./src")

(def cli-options 
  [["-a" "--analysis" :parse-fn keyword]
   ["-h" "--help"]])

(defn -main [& args]

  (let [{:keys [arguments] {:keys [analysis]} :options :as parsed} (clojure.tools.cli/parse-opts args cli-options)
        edn-out (clojure-stats.output/->EDNOut) ]
    ;; (prn parsed)
    (doseq [arg arguments]
      (clojure-stats.output/write-records edn-out (classify-files2 arg)))))
