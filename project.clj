(defproject dict-builder "0.1.0-SNAPSHOT"
  :description "A small utility to build english word frequency counts from wikipedia text."
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/tools.cli "0.3.3"]
                 [com.taoensso/carmine "2.12.2"]
                 [org.clojure/data.xml "0.0.8"]
                 [org.clojure/core.async "0.2.374"]
                 [org.apache.lucene/lucene-core "5.4.1"]
                 [org.apache.lucene/lucene-analyzers-common "5.4.1"]
                 [org.apache.commons/commons-compress "1.10"]]
  :main ^:skip-aot dict-builder.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
