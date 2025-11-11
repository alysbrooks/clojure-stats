(ns clojure-stats.output
  (:require [hugsql.core :as hugsql]
            [hugsql.adapter.next-jdbc :as next-adapter]
            [next.jdbc]
            [next.jdbc.types]
            [io.pedestal.log :as log])
  (:import [java.io File]
           [java.sql DriverManager PreparedStatement]
           [org.duckdb DuckDBConnection])
  (:gen-class))


(defprotocol AnalysisWriter

  (write-records [this records]))


(defprotocol Connect

  (connect [this]))

(defrecord EDNOut [file-prefix]

  AnalysisWriter

  (write-records [this records]
    (prn records)))

(hugsql/def-db-fns "clojure_stats/output.sql")

(defrecord DuckDBOut [url file-prefix]
  Connect
  (connect [this]
    (hugsql/set-adapter! (next-adapter/hugsql-adapter-next-jdbc))
    (let  [db (next.jdbc/get-datasource {:dbtype "duckdb" :host :none :dbname url})]
      (with-open [connection (next.jdbc/get-connection db)]

        (create-form-type connection)
        (create-tables connection))
      (assoc this :db db)))
  AnalysisWriter
  (write-records [{:keys [db file-prefix] :as _this} records]
    (let [start (System/nanoTime)
          file-records (->> records
                            (map #(get-in % [:meta :file]))
                            (apply hash-set)
                            (mapv vector))
          records (->> records
                      (map (fn [{{:keys [line column file]} :meta :keys [id parent-id root-id depth type resolved-symbol meta form clojure-type] :as _record}]
                             [id parent-id root-id depth file (name type) (str form) (str resolved-symbol) (str meta) (str clojure-type) line column ]))
                      (sort-by :id  )
                      (into []))]
      (with-open [connection (next.jdbc/get-connection db)]
        (insert-files connection {:vals file-records})

        (insert-forms connection {:vals records}))
      ;; Not sure how I feel about these nested lets
      (let [parent-dir file-prefix 
            pattern1 (str parent-dir "/(.*?)/") 
            pattern2 (str parent-dir "/.*?/(.*?)/") 
        dir-patterns {:pattern1 pattern1 
                      :pattern2 pattern2}]
        (with-open [connection (next.jdbc/get-connection db)]
          (insert-repositories-from-files connection dir-patterns))
        (with-open [connection (next.jdbc/get-connection db)]
          (add-repo-ids connection dir-patterns)))
      (let [elapsed (/ (- (System/nanoTime) start) 1e6)
            per-record (/ elapsed (count records)) ]
        (log/info :msg (format "Processed in %.2f ms (%.2f/ms record)" elapsed per-record))))))

(defrecord DuckDBBatchOut [url file-prefix]
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
        (log/info :msg (format "Processed in %.2f ms (%.2f/ms record)" elapsed per-record))))))
