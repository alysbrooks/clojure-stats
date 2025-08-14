(ns clojure-stats.output)

(defprotocol AnalysisWriter

  (write-records [this records]))


(defrecord EDNOut []
  

  AnalysisWriter

  (write-records [this records]
    (prn records)))


