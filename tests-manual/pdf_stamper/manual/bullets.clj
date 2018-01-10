;; This test is designed to show how bullets are handled.
(ns pdf-stamper.manual.bullets
  (:require
    [pdf-stamper :refer :all]
    [clojure.edn :as edn]))

(def template-1 (edn/read-string (slurp "test/templates/bullets/template-1.edn")))
(def template-pdf-1 "test/templates/bullets/template-1.pdf")

(def text-list
  "<p><ul><li>boundaries and was definitely not written</li><li>ing about texts staying put</li><li>better than a random lorem</li><li>is a text that was</li><li>Five</li><li>Six</li></ul></p>")

(def context (->> base-context
                  (add-template template-1 template-pdf-1)))

(def pages
  [{:template :template-1
    :locations {:bullets-standard {:contents {:text text-list}}
                :bullets-custom {:contents {:text text-list}}}}])

(def out (fill-pages pages context))
(.writeTo out (java.io.FileOutputStream. "out/bullets.pdf"))
(.close out)

