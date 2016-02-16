(ns dict-builder.core
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.data.xml :as xml]
            [clojure.core.async :as async]
            [taoensso.carmine :as car :refer (wcar)])
  (:import (org.apache.commons.compress.compressors CompressorStreamFactory)
           (org.apache.lucene.analysis.tokenattributes CharTermAttribute)
           (org.apache.lucene.analysis.en EnglishAnalyzer))
  (:gen-class))

;;; globals

;; default filename
(def filename "/home/richard/enwiki-20150602-pages-articles-multistream.xml.gz")

(def cli-options
  [["-d" "--data-file DATAFILE" "Location of bz2 compressed wikipedia data file."
    :default filename]
   ["-h" "--help"]])

;; our redis connection
(def server1-conn nil) ; nil == default args

;; pipelined execution is useful
(defmacro wcar* [& body] `(car/wcar server1-conn ~@body))

;; analyzer for string tokenization
(def analyzer (EnglishAnalyzer.))

;; document processed count
(def counter (atom 0))

;;; implementation.

(defn get-node
  "Step through an xml node and return the first child element with
  the desired name."
  [element tag]
  (first (filter (fn [x] (= (get x :tag) tag)) (:content element))))

(defn article-text-seq
  "Parse the wikipedia xml and return a lazy sequence of page
  contents. Meta-pages such as redirects are ignored."
  [filename]
  (-> (io/input-stream filename)
      (#(.createCompressorInputStream (CompressorStreamFactory.) %))
      xml/parse
      :content
      (#(filter (fn [x] (= (get x :tag) :page)) %))
      (#(map (fn [x] (first (:content (get-node (get-node x :revision):text)))) %))
      ;; kill documents that just have redirects
      (#(filter (fn [x] (not (str/starts-with? x "#"))) %))))

(defn char-term-attribute-seq
  "Return a lazy sequence of analyzed strings from a TokenStream."
  [ts]
  (take-while
   some?
   (repeatedly
    (fn []
      (if (not (.incrementToken ts))
        nil
        (.toString (.getAttribute ts CharTermAttribute)))))))

(defn doc-words-seq
  "Analyze the passed string and return a realized seq of the
  processed terms."
  [txt]
  (let [ts (.tokenStream analyzer "content" txt)
        _ (.reset ts)
        terms (doall (char-term-attribute-seq ts))
        _ (.close ts)]
    terms))

(defn add-words-to-redis
  "For each word in words, increment the value in redis with the key
  word."
  [words]
  (wcar* (doall (map #(car/incr %) words)))
  (swap! counter inc))

(defn count-loop
  "Loop forever, print out the value of counter every 5 secs."
  []
  (loop [_ nil]
    (println "Docs processed: " @counter)
    (recur (async/<!! (async/timeout 5000)))))

(defn injest-file
  "Parse a wikipedia file, load word counts into redis."
  [filename]
  ;; start the progress printing
  (async/thread (count-loop))
  (println "starting processings")
  (doall
   (map add-words-to-redis
        (map doc-words-seq
             (article-text-seq filename)))))

(defn -main [& args]
  "Entry point."
  (let [opts (parse-opts args cli-options)
        filename (get-in opts [:options :data-file] "/tmp/nofile")]
    (if (not (.exists (io/as-file filename)))
      (println (:summary opts))
      (injest-file (str "file://" filename)))))

