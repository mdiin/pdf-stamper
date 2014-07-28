(ns pdt.pdf.images
  (:import
    [org.apache.pdfbox.pdmodel PDDocument]
    [org.apache.pdfbox.pdmodel.edit PDPageContentStream]
    [org.apache.pdfbox.pdmodel.graphics.xobject PDXObjectImage PDPixelMap]))

(defn fill-image
  "c-stream: a PDPageContentStream object
  data: a map combining the area descriptions with the data
  context: fonts, templates, etc.

  Example of area:
  {:height ..
   :width ..
   :x ..
   :y ..
   :name ..
   :type :image
   :contents {:image java.awt.BufferedImage}}"
  [document c-stream data context]
  (let [image (PDPixelMap. document (get-in data [:contents :image]))]
    (.. c-stream (drawXObject image (:x data) (:y data) (:width data) (:height data)))
    c-stream))

;;; Test data (images)
(def a-doc (PDDocument/load "template.pdf"))
(def a-page
  (-> a-doc
      (.getDocumentCatalog)
      (.getAllPages)
      (.get 0)))

(def a-edn
  (read-string (slurp "template.edn")))

(def a-input-page
  {:kind :foo
   :locations {:page/img {:location "sponsors/original/102_h9_ok.jpg"}}})

