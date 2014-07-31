(ns pdt.pdf.text.parsed
  (:require
    [pdt.context :as context]
    [clojure.string :as string]
    [clojure.zip :as zip]
    [clojure.data.xml :as xml]))

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
                                        (if (<= new-length max-chars)
                                          [new-length (conj current-line w) lines]
                                          [word-length [w] (if (seq current-line)
                                                             (conj lines current-line)
                                                             lines)])))
                                    [0 [] []]
                                    (paragraph->words paragraph))]
    (collect-paragraph (conj lines last-line))))

(defn- unbreak-paragraph
  [elem-type lines]
  {:elem elem-type
   :content (mapcat collect-line (partition-by :style
                                                (apply concat lines)))})

(def ^:private paragraph-tags #{:h1 :h2 :h3 :p :ul :ol})

(def ^:private strong-format {:style #{:bold}})
(def ^:private em-format {:style #{:italic}})

(defn- style->format
  [regular-format style-vec]
  (let [formatting (reduce (fn [format style]
                             (merge-with #((fnil into #{}) %1 %2)
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

(defn- line-length
  [formatting context]
  (let [{:keys [font style width size]} formatting
        font-width (context/get-average-font-width font style size context)
        indent-width (get-in formatting [:indent :all])]
    (- (/ width font-width)
       (/ indent-width font-width))))

(defn- line-height
  [formatting context]
  (let [{:keys [font style size]} formatting
        font-height (context/get-font-height font style size context)]
    (+ font-height
       (get-in formatting [:spacing :line :below])
       (get-in formatting [:spacing :line :above]))))

(defn get-paragraph-nodes
  [xml-string]
  (elm->pars
    (filter #(some #{(:tag %)} paragraph-tags)
            (zip/children
              (zip/xml-zip
                (xml/parse-str xml-string))))))

(defn paragraphs-overflowing
  [paragraphs formatting context]
  (rest 
    (reduce (fn [[size-left paragraphs overflow] paragraph]
              (let [actual-formatting (merge (select-keys formatting [:width])
                                             (select-keys paragraph [:elem])
                                             (get formatting (:elem paragraph)))
                    line-chars (line-length actual-formatting context)
                    paragraph-lines (break-paragraph paragraph line-chars)
                    paragraph-line-height (line-height actual-formatting context)
                    number-of-lines (/ size-left paragraph-line-height)
                    [p o] (split-at number-of-lines paragraph-lines)
                    paragraph (map #(line-style->format % actual-formatting) p)]
                (if (seq o)
                  [0
                   (if (seq paragraph)
                     (conj paragraphs [actual-formatting paragraph])
                     paragraphs)
                   (conj overflow [actual-formatting o])]
                  [(- size-left (* paragraph-line-height (count paragraph)))
                   (conj paragraphs [actual-formatting paragraph])
                   overflow])))
            [(:height formatting) [] []]
            paragraphs)))

(defn handle-overflow
  [overflow hole]
  (when (seq overflow)
    {hole {:contents {:text (map (fn
                                   [[formatting paragraph]]
                                   (unbreak-paragraph (:elem formatting) paragraph))
                                 overflow)}}}))

;;; Missing
;; - Numbered lists
;; - Partially nested character-level tags, e.g. <em><strong>foo</strong> bar</em>

