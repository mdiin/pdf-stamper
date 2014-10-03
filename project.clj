(defproject pdf-stamper "0.2.6-SNAPSHOT"
  :description "Combine template descriptions and template PDFs with data to produce PDFs."
  :url "http://mdiin.github.io/pdf-stamper"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :scm {:name "git"
        :url "https://github.com/mdiin/pdf-stamper"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/data.xml "0.0.8"] ;; XML parsing
                 [org.apache.pdfbox/pdfbox "1.8.7"] ;; PDF
                 [potemkin "0.3.9"] ;; Code organisation
                 [prismatic/schema "0.3.0"] ;; Template validations
                 ]
  :deploy-repositories [["releases" :clojars]])

