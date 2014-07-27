(ns pdt.pdf
  (:require
    [pdt.templates :as templates]
    [pdt.pdf.images :as images]
    [pdt.pdf.text :as text]
    [pdt.images :as img])
  (:import
    [org.apache.pdfbox.pdmodel PDDocument]
    [org.apache.pdfbox.pdmodel.edit PDPageContentStream]
    [org.apache.pdfbox.pdmodel.graphics.xobject PDXObjectImage PDPixelMap]))

(defn fill-page
  [document tpage tjson page]
  (let [page-contents (PDPageContentStream. document tpage true true)
        image-areas (filter #(= (:type %) :image) tjson)
        text-areas (filter #(= (:type %) :text) tjson)]
    (images/fill-images page-contents
                        document
                        (map #(assoc % :contents (get-in page [:locations (:name %)]))
                             image-areas))))


;;; Test data
(def new-cs (fill-page a-doc a-page a-edn a-input-page))

