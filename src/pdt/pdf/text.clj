(ns pdt.pdf.text
  (:require
    [pdt.context :as context]
    [pdt.pdf.text.parsed :as parsed-text]))

(defn- begin-text-block
  [c-stream]
  (.. c-stream (beginText))
  c-stream)

(defn- end-text-block
  [c-stream]
  (.. c-stream (endText))
  c-stream)

(defn- set-text-position
  [c-stream x y]
  (.. c-stream (setTextMatrix 1 0 0 1 x y))
  c-stream)

(defn- move-text-position-up
  [c-stream amount]
  (.. c-stream (moveTextPositionByAmount 0 amount))
  c-stream)

(defn- move-text-position-down
  [c-stream amount]
  (.. c-stream (moveTextPositionByAmount 0 (- amount)))
  c-stream)

(defn- move-text-position-right
  [c-stream amount]
  (.. c-stream (moveTextPositionByAmount amount 0))
  c-stream)

(defn- move-text-position-left
  [c-stream amount]
  (.. c-stream (moveTextPositionByAmount (- amount) 0))
  c-stream)

(defn- new-line-by-font
  [c-stream font size style context]
  (let [font-height (context/get-font-height font style size context)]
    (move-text-position-down c-stream font-height)))

(defn- set-font
  [c-stream font size style context]
  (let [font-obj (context/get-font font style context)]
    (.. c-stream (setFont font-obj size))
    c-stream))

(defn- set-color
  [c-stream color]
  (let [[r g b] color]
    (doto c-stream
      (.setStrokingColor r g b)
      (.setNonStrokingColor r g b))))

(defn- draw-string
  [c-stream string]
  (.. c-stream (drawString string))
  c-stream)

(defn- add-padding-horizontal
  [c-stream line-length formatting]
  (let [h-align (get-in formatting [:align :horizontal])]
    (condp = h-align
      :center (move-text-position-right c-stream (/ (- (:width formatting) line-length) 2))
      :left c-stream
      :right (move-text-position-right c-stream (- (:width formatting) line-length)))))

(defn- add-padding-vertical
  [c-stream line-height formatting]
  (let [v-align (get-in formatting [:align :vertical])]
    (condp = v-align
      :center (move-text-position-up c-stream (/ (- (:height formatting) line-height) 2))
      :top (move-text-position-up c-stream (- (:height formatting) line-height))
      :bottom c-stream)))

(defn- write-unparsed-line
  [c-stream line context]
  (let [{:keys [align width height font size style color] :as formatting} (:format line)
        line-length (context/get-font-string-width font style size (:contents line) context)
        line-height (context/get-font-height font style size context)]
    (-> c-stream
        (add-padding-horizontal line-length formatting)
        (add-padding-vertical line-height formatting)
        (set-font font size style context)
        (set-color color)
        (draw-string (:contents line)))))

(defn- write-linepart
  [c-stream linepart context]
  (let [{:keys [font size style color]} (:format linepart)]
    (-> c-stream
        (set-font font size style context)
        (set-color color)
        (draw-string (:contents linepart)))))

(defn- write-line
  [c-stream line context]
  (doseq [linepart (map #(update-in % [:contents] (fn [s] (str " " s))) line)]
    (write-linepart c-stream linepart context))
  c-stream)

(defn- write-default-paragraph
  [c-stream formatting paragraph context]
  (let [{:keys [font size style]} formatting]
    (doseq [line paragraph]
      (-> c-stream
          (move-text-position-down (get-in formatting [:spacing :line :above]))
          (write-line line context)
          (new-line-by-font font size style context)
          (move-text-position-down (get-in formatting [:spacing :line :below]))))))

(defn- write-bullet-paragraph
  [c-stream formatting paragraph context]
  (let [{:keys [font style size color bullet-char]} formatting
        bullet (str bullet-char)
        bullet-length (context/get-font-string-width font style size bullet context)]
    (-> c-stream
        (set-font font size style context)
        (set-color color)
        (move-text-position-down (get-in formatting [:spacing :line :above]))
        (move-text-position-left (* bullet-length 2))
        (draw-string bullet)
        (move-text-position-right (* bullet-length 2))
        (write-line (first paragraph) context)
        (new-line-by-font font size style context))
    (doseq [line (rest paragraph)]
      (-> c-stream
          (move-text-position-down (get-in formatting [:spacing :line :above]))
          (write-line line context)
          (new-line-by-font font size style context)
          (move-text-position-down (get-in formatting [:spacing :line :below]))))
    c-stream))

(defn- write-paragraph-internal
  [c-stream formatting paragraph context]
  (let [paragraph-type (:elem formatting)]
    (cond
      (= paragraph-type :paragraph) (write-default-paragraph c-stream formatting paragraph context)
      (= paragraph-type :bullet) (write-bullet-paragraph c-stream formatting paragraph context)
      (= paragraph-type :number) (write-bullet-paragraph c-stream formatting paragraph context)
      :default (write-default-paragraph c-stream formatting paragraph context))
    c-stream))

(defn- write-paragraph
  [c-stream formatting paragraph context]
  (-> c-stream
      (move-text-position-right (get-in formatting [:indent :all]))
      (move-text-position-down (get-in formatting [:spacing :paragraph :above]))
      (write-paragraph-internal formatting paragraph context)
      (move-text-position-down (get-in formatting [:spacing :paragraph :below]))
      (move-text-position-left (get-in formatting [:indent :all]))))

(defn- write-paragraphs
  [c-stream formatting paragraphs context]
  (doseq [[p-format paragraph] paragraphs]
    (write-paragraph c-stream p-format paragraph context))
  c-stream)

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
        (begin-text-block)
        (set-text-position (:x data) (:y data))
        (write-paragraphs formatting paragraphs context)
        (end-text-block))
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
        (begin-text-block)
        (set-text-position (:x data) (:y data))
        (write-unparsed-line text context)
        (end-text-block))
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

