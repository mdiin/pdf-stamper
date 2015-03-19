;; *Documentation intended for developers of pdf-stamper*
;;
;; Filling parsed text holes involves a lot of extra processing of the page contents. The input data is required to be a top-level XML-tag containing the actual paragraphs as children.
;; Each paragraph can contain any number of arbitrarily nested character-level tags that must be parsed and represented correctly.

(ns pdf-stamper.text.parsed
  (:require
    [pdf-stamper.context :as context]
    [clojure.string :as string]
    [clojure.zip :as zip]
    [clojure.data.xml :as xml]))

;; ## Internal representation
;;
;; While input to the parsed text holes is XML, internally it is converted to a different representation that better supports splitting the
;; contents of tags over multiple lines.
;;
;; The general structure of the internal representation of an XML string is as a seq of the representations of paragraph-level tags.
;; Paragraph-level tags are represented as maps with the keys `:elem` and `:content`. The element type defines the final output when a line
;; of that paragraph is stamped onto a PDF. The contents is either a single string (wrapped in a seq) or a seq of strings interleaved with character-level tags.
;; Character-level tags are represented as maps as well, but they have the key `:style` instead of `:elem`. The style is a vector of possible styles,
;; where the default is `:regular`. If `:regular` is present there can be no other styles in the vector.

(defprotocol PInternalRep
  "Multiple types can be converted into the internal
  representation."
  (represent [this]))

;; ### Character-level tags

(defn- inline-tag-one
  "Converting character-level tags to the internal
  representation involves handling nested character-level
  tags.

  Remember that the contents of a tag is always a
  seq. This function handles exactly on piece of the
  content seq, such that `contents` here is always a string
  (no nested tag) or a map (a nested tag).

  In the case of nested tags the new style is `conj`ed onto
  the style of the nested tag. This is also where the requirement
  that the `:regular` style cannot be present at the same
  time as other styles is enforced. Since `:regular` is never
  added by a tag, it is safe to assume that removing it this
  once is enough.

  If `contents` is not a nested tag the style is simply set to
  `[style]`."
  [contents style]
  (if-let [content-style (:style contents)]
    {:style (conj (if (= content-style [:regular])
                    []
                    content-style)
                  style)
     :contents (:contents contents)}
    {:style [style]
     :contents contents}))

(defn- inline-tag-multi
  "To handle the general case where `contents-s` is a seq, the
  parent character-level tag's style (`parent-style`) must be
  distributed to all it's children."
  [contents-s parent-style]
  (reduce (fn [acc contents]
            (conj acc
                  (inline-tag-one contents parent-style)))
          []
          contents-s))

(defn- inline-tag
  "When building the internal representation of a character-level
  tag, it is necessary to take into account the entire content
  vector. Regardless of whether there are one or many character-
  level tags in the content vector, the resulting internal
  representation is always a vector of the same size as the
  `content-vec`."
  [style]
  (fn [content-vec]
    (let [[f-contents & r-contents] (flatten content-vec)
          contents (inline-tag-one f-contents style)]
      (if (seq r-contents)
        (into [contents] (inline-tag-multi r-contents style))
        [contents]))))

;; Only two types of character-level tags are supported:
;;
;; - `:em`
;; - `:strong`
;;
;; However, these can be used for multiple actual XML character-level tags.

(def em-tag ^:private (inline-tag :em))
(def strong-tag ^:private (inline-tag :strong))

;; ### Paragraph-level tags

(defn- paragraph-tag
  "Converting a paragraph-level tag to the internal representation
  is a matter of attaching the element type and flattening the
  `content-vec`.

  `content-vec` has to be flattened because it might contain any
  number of nested character-level tags that have to be unnested."
  [elem-type]
  (fn [content-vec]
    {:elem elem-type
     :content (flatten content-vec)}))

;; Six types of paragraph-level tags are supported:
;;
;; - `:bullet`
;; - `:number`
;; - `:paragraph`
;; - `:head-1`
;; - `:head-2`
;; - `:head-3`
;;
;; As with the character-level tags they could in principle be used for multiple actual XML paragraph-level tags each; in practice that is not the case.

(def ul-li-tag ^:private (paragraph-tag :bullet))
(def ol-li-tag ^:private (paragraph-tag :number))
(def p-tag ^:private (paragraph-tag :paragraph))
(def h1-tag ^:private (paragraph-tag :head-1))
(def h2-tag ^:private (paragraph-tag :head-2))
(def h3-tag ^:private (paragraph-tag :head-3))

;; ### Putting it all together
;;
;; Texts are parsed using clojure.data.xml, so the main part of converting to the internal representation is being able to convert the XML datastructure.
;; Every `clojure.data.xml.Element` has some `:content`, which is a seq of strings and other xml elements. If the element is not of a supported type
;; the tag is stripped and the representation is built for the contents instead. This ensures that no meaning is dropped from a sentence, only markup.
;;
;; Supported XML tags are:
;;
;; - `<b>`
;; - `<strong>`
;; - `<i>`
;; - `<em>`
;; - `<ul>`
;; - `<ol>`
;; - `<p>`
;; - `<h1>`
;; - `<h2>`
;; - `<h3>`
;;
;; The semantics of each tag follow HTML, e.g. `<ul>` is an unordered list.
;;
;; *Future*: Ordered lists, i.e. `:number` in the internal representation, are not yet fully supported. Actually printing numbers instead of just bullets is missing.

(extend-protocol PInternalRep
  java.lang.String
  (represent [s] {:style [:regular] :contents (string/trim s)})

  clojure.data.xml.Element
  (represent [e]
    (condp = (:tag e)
      :b (strong-tag (represent (:content e)))
      :strong (strong-tag (represent (:content e)))
      :i (em-tag (represent (:content e)))
      :em (em-tag (represent (:content e)))
      :ul (map ul-li-tag (represent (:content e)))
      :ol (map ol-li-tag (represent (:content e)))
      :p (p-tag (represent (:content e)))
      :h1 (h1-tag (represent (:content e)))
      :h2 (h2-tag (represent (:content e)))
      :h3 (h3-tag (represent (:content e)))
      (represent (:content e))))

  clojure.lang.LazySeq
  (represent [s]
    (map represent s)))

;; Because the internal representations can be arbitrarily nested it is necessary to flatten the seq before proceeding.
(def flat-represent ^:private (comp flatten represent))

(def ^:private paragraph-tags #{:h1 :h2 :h3 :p :ul :ol})

(defn get-paragraph-nodes
  "Getting the internal representation of the supported
  paragraph tags in an XML string is simple: Parse it using
  clojure.data.xml, filter on the supported tag names, and
  flatten the internal representation."
  [xml-string]
  (flat-represent
    (filter #(some #{(:tag %)} paragraph-tags)
            (zip/children
              (zip/xml-zip
                (xml/parse-str xml-string :supporting-external-entities true))))))

;; ## Formatting
;;
;; The conversion of styles to actual formatting instructions used by the code that does the stamping of text to the PDF.
;;
;; Every internal representation of a character-level tag needs to know which font style it corresponds to.
(def ^:private strong-format {:style #{:bold}})
(def ^:private em-format {:style #{:italic}})

(defn- style->format
  "The font style of a formatting map is updated
  according to the styles present in the `style-vec`.

  Example: If `style-vec` is `[:bold :italic]`, the
  resulting map will have the key `:style` point to
  `#{:bold :italic}`."
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
  "Adding font style information to a line means
  adding font style information to each part of that
  line. It is expected that a line is split into
  parts that each have the same styling."
  [line formatting]
  (map (fn [line-part]
         (assoc line-part :format (style->format formatting (:style line-part))))
       line))

;; ## Line breaking & unbreaking
;;
;; Note the use of the word line-part in the previous section: All words in a line-path have the same styling. This is not necessarily true for full lines.

;; ### Splitting lines
(defn- line->words
  "Distribute the styles of a line on it's individual
  words, and return a seq of words. This allows lines
  to be broken and reassembled as required by the line-
  breaking algorithm."
  [line]
  (let [style (:style line)]
    (when-let [line-contents (:contents line)]
      (map (fn [word]
             {:style style :contents word})
           (string/split (:contents line) #" ")))))

(defn- paragraph->words
  [paragraph]
  (mapcat line->words (:content paragraph)))

;; ### Collecting (un-splitting) lines
(defn- words->line
  "Reassemble a line-part from a seq of words by making
  the style of the first word the style for the entire
  line-part

  *Assumption*: All words in `words` have the same style!"
  [words]
  (let [[w & ws] words
        style (:style w)]
    {:style style
     :contents (->> words
                    (map :contents)
                    (string/join " "))}))

(defn- collect-line
  "Reassemble an entire line of words into line-parts
  by merging groups of words with the same style (in
  order of appearance in the line)."
  [line]
  (map words->line (partition-by :style line)))

(defn- collect-paragraph
  [paragraph]
  (map collect-line paragraph))

;; ### Line-breaking algorithm

(defn- break-paragraph
  "Break a paragraph into lines of `max-chars` length
  by splitting it into it's constituent parts (words)
  and reassembling with lines of the right length."
  [paragraph formatting context]
  (let [max-width (:width formatting)
        {:keys [font style size]} formatting
        space-width (context/get-font-string-width font style size " " context)
        [_ last-line lines] (reduce (fn [acc w]
                                      (let [{:keys [contents]} w
                                            [current-length current-line lines] acc
                                            word-length (+ (context/get-font-string-width font style size contents context)
                                                           space-width)
                                            new-length (+ current-length word-length)]
                                        (if (<= new-length max-width)
                                          [new-length (conj current-line w) lines]
                                          [word-length [w] (if (seq current-line)
                                                             (conj lines current-line)
                                                             lines)])))
                                    [0 [] []]
                                    (paragraph->words paragraph))]
    (collect-paragraph (conj lines last-line))))

(defn- unbreak-paragraph
  "Reassemble a paragraph from a seq of lines by setting
  a particular element type for the paragraph (i.e. you
  have to know in advance which kind of paragraph you are
  reassembling)."
  [formatting lines]
  (merge 
    (select-keys formatting [:broken :elem])
    {:content (mapcat collect-line (partition-by :style
                                                (apply concat lines)))}))

;; ## Text overflow

(defn- line-length
  "*Future*: This is one of the places that has to be
  extended to support indenting only the first line of
  a paragraph."
  [formatting context]
  (let [{:keys [font style width size]} formatting
        font-width (context/get-average-font-width font style size context)
        indent-width (get-in formatting [:indent :all])]
    (- (/ width font-width)
       (/ indent-width font-width))))

(defn- line-height
  [formatting context]
  (let [{:keys [font style size]} formatting
        font-height (context/get-font-height font style size context)
        font-leading (context/get-font-leading font style size context)]
    (+ font-height
       font-leading
       (get-in formatting [:spacing :line :below])
       (get-in formatting [:spacing :line :above]))))

(defn paragraphs-overflowing
  "When stamping paragraphs of text to a hole it is not
  certain that enough space is available. To figure out
  which paragraphs did not fit in the hole it is necessary
  to know:

  - The height of each line in the paragraphs ;; 1
  - The number of lines for each paragraph  ;; 2

  With that knowledge it is possible to compute which
  lines of a given paragraph did not fit in the hole (;; 3),
  and should thus be added to the overflow.
  
  Any paragraphs following the first paragraph that
  overflowed will naturally have their lines added to the
  overflow (;; 4)."
  [paragraphs formatting context]
  (rest
    (reduce (fn [[size-left paragraphs overflow] paragraph]
              (let [actual-formatting (merge (select-keys formatting [:width])
                                             (select-keys paragraph [:elem :broken])
                                             (get formatting (:elem paragraph)))
                    line-chars (line-length actual-formatting context)
                    paragraph-lines (break-paragraph paragraph actual-formatting context)
                    paragraph-line-height (line-height actual-formatting context) ;; 1
                    number-of-lines (Math/floor
                                      (/ size-left paragraph-line-height)) ;; 2
                    [p o] (split-at number-of-lines paragraph-lines) ;; 3
                    paragraph (map #(line-style->format % actual-formatting) p)]
                (if (seq o)
                  [0
                   (if (seq paragraph)
                     (conj paragraphs [actual-formatting paragraph])
                     paragraphs)
                   ;; TODO: There is a bug here, when a paragraph is exactly as long as the space given,
                   ;; the following paragraph will be marked as `:broken`. See issue #31
                   (conj overflow [(assoc actual-formatting :broken (if (= (count overflow) 0) true false)) o])] ;; 4
                  [(- size-left 
                      (* paragraph-line-height (count paragraph))
                      (get-in actual-formatting [:spacing :paragraph :above])
                      (get-in actual-formatting [:spacing :paragraph :below]))
                   (conj paragraphs [actual-formatting paragraph])
                   overflow])))
            [(:height formatting) [] []]
            paragraphs)))

(defn handle-overflow
  "Any overflow is reassembled to a map that tells the
  stamping algorithm where to write on an eventual new
  page."
  [overflow hole]
  (when (seq overflow)
    {hole {:contents {:text (map (fn
                                   [[formatting paragraph]]
                                   (unbreak-paragraph formatting paragraph))
                                 overflow)}}}))

