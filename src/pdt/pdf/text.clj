(ns pdt.pdf.text
  (:require
    [pdt.pdf.text.parsed :as parsed-text]
    [pdt.pdf.text.pdfbox :as pdf]))

(defn fill-text-parsed
  "document: the PDDocument object that is the final product
  c-stream: a PDPageContentStream object
  data: a map combining the area descriptions with the data
  context: fonts, templates, etc.

  Example of area:
  {:height ..
   :width ..
   :x ..
   :y ..
   :name ..
   :type :text
   :format {:font ...
            :style ...
            :size ...
            :color ...}
   :contents {:text a-seq-of-paragraphs}}"
  [document c-stream data context]
  (let [formatting (merge (:format data)
                          (select-keys data [:width :height]))
        [paragraphs overflow] (parsed-text/paragraphs-overflowing
                                (get-in data [:contents :text])
                                formatting
                                context)]
    (-> c-stream
        (pdf/begin-text-block)
        (pdf/set-text-position (:x data) (:y data))
        (pdf/write-paragraphs formatting paragraphs context)
        (pdf/end-text-block))
    (parsed-text/handle-overflow overflow (:name data))))

(defn fill-text
  "document: the PDDocument object that is the final product
  c-stream: a PDPageContentStream object
  data: a map combining the hole descriptions with the data
  context: fonts, templates, etc."
  [document c-stream data context]
  (let [formatting (merge (:format data)
                          (select-keys data [:align :width :height]))
        text {:contents (get-in data [:contents :text])
              :format formatting}]
    (-> c-stream
        (pdf/begin-text-block)
        (pdf/set-text-position (:x data) (:y data))
        (pdf/write-unparsed-line text context)
        (pdf/end-text-block))
    nil))

;;; Missing
;; - Numbered lists
;; - Space after paragraph lines (as opposed to below paragraphs)
;; - Partially nested character-level tags, e.g. <em><strong>foo</strong> bar</em>

;;; Test data (text)
;(def ps (get-paragraph-nodes (str "<div>" text "</div>")))
;(def p (first (rest ps)))

(comment
(def edn
  (read-string (slurp "template-b.edn")))

(def text
  "<h1>Banebeskrivelse</h1><p>Ham aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa<em><strong>hock</strong> ullamco</em> quis, t-bone biltong kielbasa sirloin prosciutto non <b>ribeye</b> andouille chuck mollit.</p><h2>Regler</h2><p>Sausage commodo ex cupidatat in pork loin. Ham leberkas sint pork chop bacon. <em><b>Chuck ea dolor</b></em>, salami sausage ad duis tongue officia nisi veniam pork belly cupidatat.</p><p>test number two</p><h3>Huskeliste</h3><ul><li>one time <b>ape</b> and duck</li><li>two times <em><b>ape</b></em> and duck</li></ul>")

(def templates
  (templates/add-template edn "template.pdf" {}))

(def template (:template-b templates))

(def context
  (-> {}
      (assoc :templates templates)))

(def data (merge (first (:holes template)) {:contents {:text (str "<t>" text "</t>")}}))

(def ps (get-paragraph-nodes (str "<div>" text "</div>")))

(def input-page
  {:kind :foo
   :locations {:page/text {:text text}}})

(def doc-1 (PDDocument/load "template.pdf"))
(def page-1
  (-> doc-1
      (.getDocumentCatalog)
      (.getAllPages)
      (.get 0)))

(def doc-2 (PDDocument/load "template.pdf"))
(def page-2
  (-> doc-2
      (.getDocumentCatalog)
      (.getAllPages)
      (.get 0)))

(def out-doc (PDDocument.))
(.addPage out-doc page-1)
(def c-stream-1 (PDPageContentStream. out-doc page-1 true false))
(fill-text c-stream-1 data context)
(.close c-stream-1)

(.addPage out-doc page-2)
(def c-stream-2 (PDPageContentStream. out-doc page-2 true false))
(fill-text c-stream-2 data context)
(.close c-stream-2)

(.save out-doc "test.pdf")
(.close out-doc)

(def out-doc-2 (PDDocument.))
(.addPage out-doc-2 page-1)
(def c-stream-1 (PDPageContentStream. out-doc-2 page-1 true false))
(fill-text c-stream-1 data context)
(.close c-stream-1)

(.addPage out-doc-2 page-2)
(def c-stream-2 (PDPageContentStream. out-doc-2 page-2 true false))
(fill-text c-stream-2 data context)
(.close c-stream-2)

(.save out-doc-2 "test2.pdf")
(.close out-doc-2))

;(.close doc-1)
;(.close doc-2)

