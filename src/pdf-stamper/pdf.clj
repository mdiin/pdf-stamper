(ns pdf-stamper.pdf
  (:require
    [pdf-stamper.context :as context]
    [pdf-stamper.pdf.images :as images]
    [pdf-stamper.pdf.text :as text]
    [pdf-stamper.pdf.text.parsed :as parsed-text])
  (:import
    [org.apache.pdfbox.pdmodel PDDocument]
    [org.apache.pdfbox.pdmodel.edit PDPageContentStream]))

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
        template-overflow (context/get-template-overflow template context)
        template-holes (context/get-template-holes template context)
        template-doc (context/get-template-document template context)
        template-page (-> template-doc (.getDocumentCatalog) (.getAllPages) (.get 0))
        template-c-stream (PDPageContentStream. document template-page true false)]
    (.addPage document template-page)
    (let [overflows (doall
                      (map (fn [hole]
                             (when (get-in page-data [:locations (:name hole)])
                               (condp = (:type hole)
                                 :image (let [data (merge hole
                                                          (get-in page-data
                                                                  [:locations (:name hole)]))]
                                          (images/fill-image document
                                                             template-c-stream
                                                             data
                                                             context))
                                 :text-parsed (let [data (update-in (merge hole
                                                                           (get-in page-data
                                                                                   [:locations (:name hole)]))
                                                                    [:contents :text]
                                                                    #(if (string? %) (parsed-text/get-paragraph-nodes %) %))]
                                                (text/fill-text-parsed document
                                                                       template-c-stream
                                                                       data
                                                                       context))
                                 :text (let [data (merge hole
                                                         (get-in page-data
                                                                 [:locations (:name hole)]))]
                                         (text/fill-text document
                                                         template-c-stream
                                                         data
                                                         context)))))
                           (sort-by :priority template-holes)))
          overflow-page-data {:template template-overflow
                              :locations (into {} overflows)}]
      (.close template-c-stream)
      (if (seq (:locations overflow-page-data))
        (conj (fill-page document overflow-page-data context) template-doc) ;; TODO: Stack overflow waiting to happen...
        [template-doc]))))

(defn fill-pages
  [pages context]
  (let [output (java.io.ByteArrayOutputStream.)]
    (with-open [document (PDDocument.)]
      (let [context-with-embedded-fonts (reduce (fn [context [font style]]
                                                  (context/embed-font document font style context))
                                                context
                                                (:fonts-to-embed context))
            open-documents (doall (map #(fill-page document % context-with-embedded-fonts) pages))]
        (.save document output)
        (doseq [doc (flatten open-documents)]
          (.close doc))))
    output))

