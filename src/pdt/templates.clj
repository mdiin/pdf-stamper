(ns pdt.templates
  (:require
    [clojure.edn :as edn])
  (:import
    [org.apache.pdfbox.pdmodel PDDocument]
    [org.apache.pdfbox.pdmodel.edit PDPageContentStream]
    [org.apache.pdfbox.pdmodel.graphics.xobject PDXObjectImage PDPixelMap]))

(defn add-template
  [template-str template-file templates]
  (if template-str
    (-> templates
        (assoc (:name template-str) template-str)
        (assoc-in [(:name template-str) :file] template-file))
    templates))

