(ns pdt.pdf
  (:require
    [pdt.context :as context]
    [pdt.pdf.images :as images]
    [pdt.pdf.text :as text])
  (:import
    [org.apache.pdfbox.pdmodel PDDocument]
    [org.apache.pdfbox.pdmodel.edit PDPageContentStream]
    [org.apache.pdfbox.pdmodel.graphics.xobject PDXObjectImage PDPixelMap]))

;;;
;;; Living in IO land here, so tread carefully!
;;;

(defn- fill-page
  "document: a PDDocument instance
  page-data: data for a single page
  context: fonts, templates, etc.

  Expected to return the opened PDDocument instance for the template,
  for closing after PDF is completed and saved."
  [document page-data context]
  (let [template (:template page-data)
        template-holes (context/get-template-holes template context)
        template-doc (context/get-template-document template context)
        template-page (-> template-doc (.getDocumentCatalog) (.getAllPages) (.get 0))
        template-c-stream (PDPageContentStream. document template-page true false)]
    (.addPage document template-page)
    (doseq [hole (sort-by :priority template-holes)]
      (condp = (:type hole)
        :image (images/fill-image document
                                  template-c-stream
                                  (merge hole (get-in page-data [:locations (:name hole)]))
                                  context)
        :text (text/fill-text template-c-stream
                              (merge hole (get-in page-data [:locations (:name hole)]))
                              context)))
    (.close template-c-stream)
    template-doc))

(defn fill-pages
  [pages context]
  (let [output (java.io.ByteArrayOutputStream.)]
    (with-open [document (PDDocument.)]
      (let [open-documents (doall (map #(fill-page document % context) pages))]
        (.save document output)
        (doseq [doc open-documents]
          (.close doc))))
    output))


;;; Test data

(def template-1 (read-string (slurp "test/templates/template-1.edn")))
(def template-2 (read-string (slurp "test/templates/template-2.edn")))
(def template-pdf-1 "test/templates/template-1.pdf")
(def template-pdf-2 "test/templates/template-2.pdf")

(def image-1 (javax.imageio.ImageIO/read (clojure.java.io/as-file "test/templates/image-1.jpg")))
(def text-1
  "<pp><h1>Banebeskrivelse</h1><p>Ham aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa<em><strong>hock</strong> ullamco</em> quis, t-bone biltong kielbasa sirloin prosciutto non <b>ribeye</b> andouille chuck mollit.</p><h2>Regler</h2><p>Sausage commodo ex cupidatat in pork loin. Ham leberkas sint pork chop bacon. <em><b>Chuck ea dolor</b></em>, salami sausage ad duis tongue officia nisi veniam pork belly cupidatat.</p><p>test number two</p><h3>Huskeliste</h3><ul><li>one time <b>ape</b> and duck</li><li>two times <em><b>ape</b></em> and duck</li></ul></pp>")

(def context (->> context/base-context
                  (context/add-template template-1 template-pdf-1)
                  (context/add-template template-2 template-pdf-2)))

(def pages
  [{:template :template-1
    :locations {:one {:contents {:image image-1}}
                :two {:contents {:text text-1}}}}

   {:template :template-2
    :locations {:one {:contents {:text text-1}}}}])

(def out (fill-pages pages context))

