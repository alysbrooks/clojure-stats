(ns clojure-stats.output
  (:require [hugsql.core :as hugsql]
            [hugsql.adapter.next-jdbc :as next-adapter]
            [next.jdbc]
            [next.jdbc.types])
  (:import [java.io File]
           [java.sql DriverManager PreparedStatement]
           [org.duckdb DuckDBConnection])
  (:gen-class))


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
      (create-tables db)
      (assoc this :db db)))
  AnalysisWriter
  (write-records [{:keys [db] :as _this} records]
    (let [start (System/nanoTime)
          file-records (->> records
                            (map #(get-in % [:meta :file]))
                            (apply hash-set)
                            (mapv vector))
          records (->> records
                      (mapv (fn [{{:keys [line column file]} :meta :keys [type resolved-symbol meta form clojure-type] :as _record}]
                             [file (name type) (str form) (str resolved-symbol) (str meta) (str clojure-type) line column ])))]
      (insert-files db {:vals file-records})

      (insert-forms db {:vals records})
      (let [elapsed (/ (- (System/nanoTime) start) 1e6)
            per-record (/ elapsed (count records)) ]
        (println (format "Processed in %.2f ms (%.2f/ms record)" elapsed per-record))))))

(defrecord DuckDBBatchOut [url]
  Connect
  (connect [this]
    (hugsql/set-adapter! (next-adapter/hugsql-adapter-next-jdbc))

    (let  [db (next.jdbc/get-datasource {:dbtype "duckdb" :host :none :dbname url})]

      (create-form-type db)
      (create-tables db)
      (assoc this :db db)))
  AnalysisWriter
  (write-records [{:keys [db url] :as _this} records]
    (let [start (System/nanoTime)
          direct-connection ^DuckDBConnection (DriverManager/getConnection (str "jdbc:duckdb:" url))
          records (->> records
                      (mapv (fn [{:keys [type resolved-symbol meta form] :as _record}]
                             [(name type) (str resolved-symbol) (str meta) (str form)])))
          statement (.prepareStatement direct-connection " INSERT INTO forms (form, resolved_symbol, meta, clojure_type) VALUES (?::VARCHAR, ?, ?, ?, ?::VARCHAR);")]

      (doseq [{:keys [type form resolved-symbol meta clojure-type] :as _record} records]
        (doto statement
          (.setObject 1 (str form))
          (.setObject 2 (str resolved-symbol))
          (.setObject 3 (str meta))
          (.setObject 4 (str clojure-type))
          (.addBatch)))
        (.executeBatch statement)
      (.close statement)

      (let [elapsed (/ (- (System/nanoTime) start) 1e6)
            per-record (/ elapsed (count records)) ]
        (println (format "Processed in %.2f ms (%.2f/ms record)" elapsed per-record))))))
