(defproject pdf-stamper "0.5.0-SNAPSHOT"
  :description "Combine template descriptions and template PDFs with data to produce PDFs."
  :url "http://mdiin.github.io/pdf-stamper"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :scm {:name "git"
        :url "https://github.com/mdiin/pdf-stamper"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/data.xml "0.0.8"] ;; XML parsing
                 [org.apache.pdfbox/pdfbox "1.8.7"] ;; PDF
                 [potemkin "0.3.10"] ;; Code organisation
                 [prismatic/schema "1.0.4"] ;; Template validations
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
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.8.0"]]}
             :test {:dependencies [[org.clojure/clojure "1.7.0"]
                                   [org.clojure/test.check "0.9.0"]
                                   [org.clojure/tools.nrepl "0.2.12"]]
                    :source-paths ["test"]}
             :manual-tests {:dependencies [[org.clojure/tools.nrepl "0.2.12"]]
                            :source-paths ["tests-manual"]}})

