(ns pdt.pdf.text
  (:require
    [pdt.context :as context]
    [pdt.templates :as templates]
    [clojure.data.xml :as xml]
    [clojure.zip :as zip]
    [clojure.string :as string])
  (:import
    [org.apache.pdfbox.pdmodel PDDocument PDPage]
    [org.apache.pdfbox.pdmodel.font PDType1Font PDTrueTypeFont]
    [org.apache.pdfbox.pdmodel.edit PDPageContentStream]
    [org.apache.pdfbox.pdmodel.graphics.xobject PDXObjectImage PDPixelMap]))

(defprotocol IPDFRepresentable
  (pdf-ready [this]))

(defn- inline-tag
  [style]
  (fn [content-vec]
    (let [contents (first content-vec)]
      (if-let [content-style (:style contents)]
        {:style (conj (if (= content-style [:regular])
                        []
                        content-style)
                      style)
         :contents (:contents contents)}
        {:style [style]
         :contents contents}))))

(def em-tag ^:private (inline-tag :em))
(def strong-tag ^:private (inline-tag :strong))

(defn- paragraph-tag
  [elem-type]
  (fn [content-vec]
    {:elem elem-type
     :content content-vec}))

(def ul-li-tag ^:private (paragraph-tag :bullet))
(def ol-li-tag ^:private (paragraph-tag :number))
(def p-tag ^:private (paragraph-tag :paragraph))
(def h1-tag ^:private (paragraph-tag :head-1))
(def h2-tag ^:private (paragraph-tag :head-2))
(def h3-tag ^:private (paragraph-tag :head-3))

(defn- make-ready-elm
  [e]
  (condp = (:tag e)
    :b (strong-tag (pdf-ready (:content e)))
    :strong (strong-tag (pdf-ready (:content e)))
    :i (em-tag (pdf-ready (:content e)))
    :em (em-tag (pdf-ready (:content e)))
    :ul (map ul-li-tag (pdf-ready (:content e)))
    :ol (map ol-li-tag (pdf-ready (:content e)))
    :p (p-tag (pdf-ready (:content e)))
    :h1 (h1-tag (pdf-ready (:content e)))
    :h2 (h2-tag (pdf-ready (:content e)))
    :h3 (h3-tag (pdf-ready (:content e)))
    (if (:content e)
      (pdf-ready (:content e))
      (pdf-ready e))))

(def elm->pars ^:private (comp flatten make-ready-elm))

(extend-protocol IPDFRepresentable
  java.lang.String
  (pdf-ready [s] {:style [:regular] :contents (string/trim s)})

  clojure.data.xml.Element
  (pdf-ready [e] (make-ready-elm e))

  clojure.lang.LazySeq
  (pdf-ready [s]
    (map pdf-ready s)))

(defn- line->words
  [line]
  (let [style (:style line)]
    (map (fn [word]
           {:style style :contents word})
         (string/split (:contents line) #" "))))

(defn- paragraph->words
  [paragraph]
  (mapcat line->words (:content paragraph)))

(defn- words->line
  [words]
  (let [[w & ws] words
        style (:style w)]
    {:style style
     :contents (->> words
                    (map :contents)
                    (string/join " "))}))

(defn- collect-line
  [line]
  (map words->line (partition-by :style line)))

(defn- collect-paragraph
  [paragraph]
  (map collect-line paragraph))

(defn- break-paragraph
  [paragraph max-chars]
  (let [[_ last-line lines] (reduce (fn [acc w]
                                      (let [{:keys [contents]} w
                                            [current-length current-line lines] acc
                                            word-length (inc (count contents)) ;; inc is for the missing space at the end of each word
                                            new-length (+ current-length word-length)]
                                        (cond
                                          (and (> current-length 0)
                                               (<= new-length max-chars))
                                          [new-length (conj current-line w) lines]

                                          (> word-length max-chars)
                                          [new-length [w] (conj lines current-line)]

                                          :default
                                          [word-length [w] (conj lines current-line)]
                                          )))
                                    [0 [] []]
                                    (paragraph->words paragraph))]
    (collect-paragraph (conj lines last-line))))

(defn- unbreak-paragraph
  [elem-type lines]
  {:elem elem-type
   :content (mapcat collect-line (partition-by :style
                                                (apply concat lines)))})

(def ^:private paragraph-tags #{:h1 :h2 :h3 :p :ul :ol})

(defn get-paragraph-nodes
  [xml-string]
  (elm->pars
    (filter #(some #{(:tag %)} paragraph-tags)
            (zip/children
              (zip/xml-zip
                (xml/parse-str xml-string))))))

(def ^:private strong-format {:style :bold})
(def ^:private em-format {:style :italic})

(defn- style->format
  [regular-format style-vec]
  (let [formatting (reduce (fn [format style]
                             (merge-with #(conj %1 %2)
                                         format
                                         (condp = style
                                           :regular regular-format
                                           :strong strong-format
                                           :em em-format
                                           {})))
                           {:style #{}}
                           style-vec)]
    (merge regular-format formatting)))

(defn- line-style->format
  [line formatting]
  (map (fn [line-part]
         (assoc line-part :format (style->format formatting (:style line-part))))
       line))

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

(defn- new-line-by-font-size
  [c-stream font-size]
  (move-text-position-down c-stream (* font-size 1.5)))

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
        font (context/get-font font style context)
        line-length (* size (/ (.. font (getStringWidth (:contents line))) 1000))
        line-height size]
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

(defn- write-paragraph
  [c-stream formatting paragraph context]
  (doseq [line paragraph]
    (-> c-stream
        (move-text-position-down (get-in formatting [:spacing :before]))
        (write-line (line-style->format line formatting) context)
        (move-text-position-down (get-in formatting [:spacing :after]))))
  c-stream)

(defn- line-length
  [formatting]
  (let [{:keys [font style width size]} formatting
        font (context/get-font font style context)]
    (/ width (* (/ (.. font (getAverageFontWidth)) 1000) size))))

(defn- line-height
  [formatting]
  (let [{:keys [font style size]} formatting
        font (context/get-font font style context)
        font-height #spy/d (/ (.. font (getFontDescriptor) (getFontBoundingBox) (getHeight)) 1000)]
    (+ (* font-height size)
       (get-in formatting [:spacing :after])
       (get-in formatting [:spacing :before]))))

(defn- write-paragraphs
  [c-stream formatting paragraphs context]
  (let [[_ paragraphs-to-draw overflow] (reduce (fn [[size-left paragraphs overflow] paragraph]
                                                  (let [actual-formatting (merge (select-keys formatting [:width])
                                                                                 (select-keys paragraph [:elem])
                                                                                 (get formatting (:elem paragraph)))
                                                        line-chars (line-length actual-formatting)
                                                        paragraph-lines (break-paragraph paragraph line-chars)
                                                        paragraph-line-height (line-height actual-formatting)
                                                        number-of-lines (/ size-left paragraph-line-height)
                                                        [p o] (split-at number-of-lines paragraph-lines)]
                                                    (if (seq o)
                                                      [0 (conj paragraphs [actual-formatting p]) (conj overflow [actual-formatting o])]
                                                      [(- size-left (* paragraph-line-height (count p))) (conj paragraphs [actual-formatting p]) overflow])))
                                                [(:height formatting) [] []]
                                                paragraphs)]
    (doseq [[p-format paragraph] paragraphs-to-draw]
      (write-paragraph c-stream p-format paragraph context))
    [c-stream overflow]))

(defn- handle-overflow
  [overflow hole]
  (when (seq overflow)
    {hole {:contents {:text (map (fn
                                   [[formatting paragraph]]
                                   (unbreak-paragraph (:elem formatting) paragraph))
                                 overflow)}}}))

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
        paragraphs (get-in data [:contents :text])]
    (-> c-stream
        (begin-text-block)
        (set-text-position (:x data) (:y data))
        (write-paragraphs formatting paragraphs context)
        (#(do (end-text-block (first %)) (second %)))
        (handle-overflow (:name data)))))

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
;; - Custom fonts
;; - Space before and after paragraph lines (as opposed to above and below paragraphs)

;;; Test data (text)
(def ps (get-paragraph-nodes (str "<div>" text "</div>")))
(def p (first (rest ps)))

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

