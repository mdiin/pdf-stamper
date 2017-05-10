;; This test is designed to show use of partial templates.
(ns pdf-stamper.manual.template-partials
  (:require
    [pdf-stamper :refer :all]
    [pdf-stamper.context :as ctx]
    [clojure.edn :as edn]))

(def template-1 (edn/read-string (slurp "test/templates/template_partials/template-1.edn")))
(def partial-1 (edn/read-string (slurp "test/templates/template_partials/partial-1.edn")))
(def partial-2 (edn/read-string (slurp "test/templates/template_partials/partial-2.edn")))
(def template-pdf-1 "test/templates/align_single/template-1.pdf")

(def background (javax.imageio.ImageIO/read (clojure.java.io/as-file "test/templates/template_partials/background.png")))
(def text "Ålquiver")

(def context (->> base-context
                  (add-template-partial partial-1)
                  (add-template-partial partial-2)
                  (add-template template-1 template-pdf-1)))

(def pages
  [{:template :template-1
    :locations {:centered-image {:contents {:image background}}
                :centered-text {:contents {:text text}}
                :top-image {:contents {:image background}}
                :top-text {:contents {:text text}}
                :bottom-image {:contents {:image background}}
                :bottom-text {:contents {:text text}}}}])

(def out (fill-pages pages context))
(.writeTo out (java.io.FileOutputStream. "out/template-partials.pdf"))
(.close out)

