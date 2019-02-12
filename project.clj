(defproject pdf-stamper "0.6.1-SNAPSHOT"
  :description "Combine template descriptions and template PDFs with data to produce PDFs."
  :url "http://mdiin.github.io/pdf-stamper"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :scm {:name "git"
        :url "https://github.com/mdiin/pdf-stamper"}
  :dependencies [[org.clojure/clojure "1.9.0" :scope "provided"]
                 [org.clojure/data.xml "0.0.8"] ;; XML parsing
                 [org.apache.pdfbox/pdfbox "2.0.11"] ;; PDF
                 [potemkin "0.4.5"] ;; Code organisation
                 [prismatic/schema "1.1.9"] ;; Template validations
                 ]
  :deploy-repositories [["releases" :clojars]]
  :source-paths ["src"]
  :release-tasks [["vcs" "assert-committed"]
                  ["change" "version"
                   "leiningen.release/bump-version" "release"]
                  ["vcs" "commit"]
                  ["vcs" "tag" "--no-sign"]
                  ["deploy"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["vcs" "commit"]
                  ["vcs" "push"]]
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.9.0"]]}
             :test {:dependencies [[org.clojure/clojure "1.9.0"]
                                   [org.clojure/test.check "0.9.0"]
                                   [org.clojure/tools.nrepl "0.2.13"]]
                    :source-paths ["test"]}
             :manual-tests {:dependencies [[org.clojure/tools.nrepl "0.2.13"]]
                            :source-paths ["tests-manual"]}})

