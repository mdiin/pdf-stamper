(ns pdf-stamper.pdf.text
  (:require
    [pdf-stamper.pdf.text.parsed :as parsed-text]
    [pdf-stamper.pdf.text.pdfbox :as pdf]))

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

