(ns pdt.pdf.text
  (:require
    [pdt.pdf.text.fonts :as fonts]
    [clojure.data.xml :as xml]
    [clojure.zip :as zip]
    [clojure.string :as string])
  (:import
    [org.apache.pdfbox.pdmodel PDDocument]
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
  (pdf-ready [s] {:style [:regular] :contents s})

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
                                          [new-length [(assoc w :contents "...")] (conj lines current-line)]   

                                          :default
                                          [word-length [w] (conj lines current-line)]
                                          )))
                                    [0 [] []]
                                    (paragraph->words paragraph))]
    (collect-paragraph (conj lines last-line))))

(def ^:private paragraph-tags #{:h1 :h2 :h3 :p :ul :ol})

(defn- get-paragraph-nodes
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

(defn- move-text-position-down
  [c-stream amount]
  (.. c-stream (moveTextPositionByAmount 0 (- amount)))
  c-stream)

(defn- new-line-by-font-size
  [c-stream font-size]
  (move-text-position-down c-stream (* font-size 1.5)))

(defn- set-font
  [c-stream font size style]
  (let [font-obj (fonts/get-font font style)]
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

(defn- write-linepart
  [c-stream linepart]
  (let [{:keys [font size style color]} (:format linepart)]
    (-> c-stream
        (set-font font size style)
        (set-color color)
        (draw-string (:contents linepart)))))

(defn- write-line
  [c-stream line]
  (doseq [linepart line]
    (write-linepart c-stream linepart))
  c-stream)

(defn- write-paragraph
  [c-stream formatting paragraph]
  (doseq [line paragraph]
    (-> c-stream
        (move-text-position-down (get-in formatting [:spacing :before]))
        (write-line (line-style->format line formatting))
        (move-text-position-down (get-in formatting [:spacing :after]))))
  c-stream)

(defn- line-length
  [formatting]
  (/ (:width formatting) (:size formatting)))

(defn- line-height
  [formatting]
  (+ (:size formatting)
     (get-in formatting [:spacing :after])
     (get-in formatting [:spacing :before])))

(defn- write-paragraphs
  [c-stream formatting paragraphs]
  (let [[_ paragraphs-to-draw overflow] (reduce (fn [[size-left paragraphs overflow] paragraph]
                                                  (let [actual-formatting (merge (select-keys formatting [:width])
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
      (write-paragraph c-stream p-format paragraph)))
  c-stream)

(defn fill-text
  "c-stream: a PDPageContentStream object
  data: a map combining the area descriptions with the data

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
   :contents {:text \"XML string\"}}"
  [c-stream data]
  (let [formatting (merge (:format data)
                          (select-keys data [:width :height]))
        paragraphs (get-paragraph-nodes (get-in data [:contents :text]))]
    (-> c-stream
        (begin-text-block)
        (set-text-position (:x data) (:y data))
        (write-paragraphs formatting paragraphs)
        (end-text-block))))

;;; Missing
;; - Page breaking

;;; Test data (text)
(def edn
  (read-string (slurp "template-b.edn")))

(def text
  "<h1>Banebeskrivelse</h1><p>Ham aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa<em><strong>hock</strong> ullamco</em> quis, t-bone biltong kielbasa sirloin prosciutto non <b>ribeye</b> andouille chuck mollit.</p><h2>Regler</h2><p>Sausage commodo ex cupidatat in pork loin. Ham leberkas sint pork chop bacon. <em><b>Chuck ea dolor</b></em>, salami sausage ad duis tongue officia nisi veniam pork belly cupidatat.</p><p>test number two</p><h3>Huskeliste</h3><ul><li>one time <b>ape</b> and duck</li><li>two times <em><b>ape</b></em> and duck</li></ul>")

(def data (merge (first edn) {:contents {:text (str "<t>" text "</t>")}}))

(def ps (get-paragraph-nodes (str "<div>" text "</div>")))

(def input-page
  {:kind :foo
   :locations {:page/text {:text text}}})

(def doc (PDDocument/load "template.pdf"))
(def page
  (-> doc
      (.getDocumentCatalog)
      (.getAllPages)
      (.get 0)))

(def out-doc (PDDocument.))
(.addPage out-doc page)
(def c-stream (PDPageContentStream. out-doc page true false))

;(fill-text c-stream data)
;(.close c-stream)
;(.save out-doc "test.pdf")
;(.close out-doc)
;(.close doc)

