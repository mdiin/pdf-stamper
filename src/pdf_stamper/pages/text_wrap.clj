(ns pdf-stamper.pages.text-wrap
  (:require
    [clojure.string :as string]
    [pdf-stamper.context :as context]
    [pdf-stamper.text.parsed :as text-parsed]))

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
                   (conj overflow [(assoc actual-formatting :broken true) o])] ;; 4
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


(defn explode-text-location
  "Explode a text location to a seq of text locations by
  using knowledge of the allotted space on each page for
  that text location. Also converts the XML text to an
  internal representation."
  [template formatting location-paragraphs context]
  (let [overflow-template (context/get-template-overflow template context)
        overflow-template-holes (context/get-template-holes overflow-template context)
        overflow-formatting (filter #(= (:name %) (:name formatting)) overflow-template-holes)
        [paragraphs overflow] (paragraphs-overflowing location-paragraphs formatting context)]
    (conj
      (handle-overflow paragraphs (:name formatting))
      (explode-text-location overflow-template overflow-formatting overflow context))))

