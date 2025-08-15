(ns clojure-stats.output
  (:require [hugsql.core :as hugsql]
            [hugsql.adapter.next-jdbc :as next-adapter]
            [next.jdbc]
            [next.jdbc.types]
            )
  (:import [java.io File]))


(defprotocol AnalysisWriter

  (write-records [this records]))


(defprotocol Connect 

  (connect [this]))

(defrecord EDNOut []
  
  AnalysisWriter

  (write-records [this records]
    (prn records)))

(hugsql/def-db-fns "clojure_stats/output.sql")

(defrecord DuckDBOut [url]
  Connect
  (connect [this]
    (hugsql/set-adapter! (next-adapter/hugsql-adapter-next-jdbc))
    

    (let  [db (next.jdbc/get-datasource {:dbtype "duckdb" :host :none :dbname url})]
      
      (create-form-type db)
      (create-forms-table db)
      (assoc this :db db)))
  AnalysisWriter
  (write-records [{:keys [db] :as _this} records]
    (let [start (System/nanoTime)]
      (doseq [record records]
        (let [record-processed (-> record 
                                   (update :type name)
                                   (update :resolved-symbol str)
                                   (update :meta str)
                                   (update :form str))]
          (insert-forms db record-processed)))
      (println (format "Processed in %2f ms" (/ (- (System/nanoTime) start) 1e6)))
      
      )))


