(defproject pdf-stamper "0.1.1"
  :description "Combine template descriptions and template PDFs with data to produce PDFs."
  :url "http://github.com/mdiin/pdf-stamper"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :scm {:name "git"
        :url "https://github.com/mdiin/pdf-stamper"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/data.xml "0.0.7"] ;; XML parsing
                 [org.apache.pdfbox/pdfbox "1.8.6"] ;; PDF
                 ]
  :deploy-repositories [["releases" :clojars]])
