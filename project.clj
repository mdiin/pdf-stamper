(defproject pdt "0.1.0-SNAPSHOT"
  :description "Combine template descriptions and template PDFs with data to produce PDFs."
  :url "http://github.com/mdiin/pdt"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/data.xml "0.0.7"] ;; XML parsing
                 [org.apache.pdfbox/pdfbox "1.8.5"] ;; PDF
                 ])
