(ns pdf-stamper.page-maker
  (:require
    [clojure.data.xml :as xml]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as stest]

    [pdf-stamper.context :as context]
    [pdf-stamper.tokenizer :refer [tokenize]]
    [pdf-stamper.tokenizer.protocols :as p :refer [Selectable]]
    [pdf-stamper.tokenizer.tokens :as t]
    [pdf-stamper.tokenizer.xml :as xml-tokenizable]))

;; The general algorithm for building pages:
;;
;; 1. Take a piece of data
;; 2. Take the template to use for that data
;; 3. Choose a hole in that template
;; 4. If it is a `:parsed-text` hole:
;;   a. Take the number of tokens for which there is room in the hole
;;   b. Add those tokens to the page in that hole (use the template as the base)
;;   c. Drop those tokens from the data set
;; 5. Repeat 3-4c for the remaining holes on the selected template
;; 6. Choose a new template based on the `:overflow` key
;; 7. Repeat 3-6 for the remaining data
;;
;; You now have a set of pages based on the data and templates.

;; Visual overview of a single data piece:
;;
;; /-----------\                            /------\
;; | Templates |                            | Data |
;; \-----------/                            \------/
;;       |                                      |
;;       |                                      |
;;  1..n |                /------\              | 1
;;       |                | Data |              |
;;       |                \------/              |
;;       \------------       +       -----------/
;;                     /-----------\
;;                     | Templates |
;;                     \-----------/
;;                           |
;;                           | 1..n
;;                           |
;;                       /-------\
;;                       | Pages |
;;                       \-------/
;;

;; Fill holes in page, respecting space constraints
;;
;; 3-4c
;;

;; 4a. Take the number of tokens for which there is room in the hole
(extend-protocol p/Selectable
  pdf_stamper.tokenizer.tokens.Word
  (select [token [tokens-selected tokens-remaining] {:as remaining-space :keys [width height]} formats context]
    (let [res (cond
            ;; Room for one more on the line
            (and
              (<= (p/width token formats context) width)
              (<= (p/height token formats context) height))
            [tokens-selected [token] tokens-remaining]

            ;; No more room on line, but room for one more line
            (and
              (> (p/width token formats context) width)
              (<= (* (p/height token formats context) 2) height))
            [tokens-selected
             [(t/->NewLine (:style token)) token]
             tokens-remaining]

            :default
            [tokens-selected nil (into [token] tokens-remaining)])]
      res))

  (horizontal-increase? [_] false)

  pdf_stamper.tokenizer.tokens.Space
  (select [token [tokens-selected tokens-remaining] {:as remaining-space :keys [width height]} formats context]
    (cond
      (<= (p/width token formats context) width)
      (if (seq tokens-selected)
        [tokens-selected [token] tokens-remaining]
        [tokens-selected nil tokens-remaining])

      :default
      [tokens-selected nil tokens-remaining]))

  (horizontal-increase? [_] false)

  pdf_stamper.tokenizer.tokens.ListBullet
  (select [token [tokens-selected tokens-remaining] {:as remaining-space :keys [width height]} formats context]
    (cond
      ;; No more room on line, issue warning
      (<= width (p/width token formats context))
      (do
        (println "WARNING: Hole for bullet paragraph narrower than bullet character!")
        [tokens-selected nil tokens-remaining])

      :default
      [tokens-selected [token] tokens-remaining]
      ))

  (horizontal-increase? [_] false)

  pdf_stamper.tokenizer.tokens.ListNumber
  (select [token [tokens-selected tokens-remaining] {:as remaining-space :keys [width height]} formats context]
    (cond
      ;; No more room on line, issue warning
      (<= width (p/width token formats context))
      (do
        (println "WARNING: Hole for number paragraph narrower than number!")
        [tokens-selected nil tokens-remaining])

      :default
      [tokens-selected [token] tokens-remaining]))

  (horizontal-increase? [_] false)

  pdf_stamper.tokenizer.tokens.NewLine
  (select [token [tokens-selected tokens-remaining :as token-context] {:as remaining-space :keys [width height]} formats context]
    (cond
      ;; Room for one more line
      (<= (p/height token formats context) height)
      (if (seq tokens-selected)
        [tokens-selected [token] tokens-remaining]
        [tokens-selected nil tokens-remaining])

      :default
      [tokens-selected nil tokens-remaining]))

  (horizontal-increase? [_] true)

  pdf_stamper.tokenizer.tokens.ParagraphBegin
  (select [token [tokens-selected tokens-remaining] {:as remaining-space :keys [width height]} formats context]
    (cond
      (<= (p/height token formats context) height)
      [tokens-selected [token] tokens-remaining]

      :default
      [tokens-selected nil (into [token] tokens-remaining)]))

  (horizontal-increase? [_] true)

  pdf_stamper.tokenizer.tokens.ParagraphEnd
  (select [token [tokens-selected tokens-remaining] {:as remaining-space :keys [width height]} formats context]
    ;; Always room for a paragraph to end.
    [tokens-selected [token] tokens-remaining])

  (horizontal-increase? [_] true)

  pdf_stamper.tokenizer.tokens.NewParagraph
  (select [token [tokens-selected tokens-remaining] {:as remaining-space :keys [width height]} formats context]
    (cond
      ;; Room for a new paragraph
      (<= (p/height token formats context) height)
      [tokens-selected [token] tokens-remaining]

      :default
      [tokens-selected nil (into [token] tokens-remaining)]))

  (horizontal-increase? [_] true)

  pdf_stamper.tokenizer.tokens.NewPage
  (select [token [tokens-selected tokens-remaining] {:as remaining-space :keys [width height]} formats context]
    ;; Always room for a page break...
    [tokens-selected [token] tokens-remaining])

  (horizontal-increase? [_] true)

  nil
  (select [_ [tokens-selected tokens-remaining] _ _ _] [tokens-selected nil tokens-remaining])
  (horizontal-increase? [_] false))

(s/def ::token (s/and (partial satisfies? p/Dimensions)
                      (partial satisfies? p/Selectable)))
(s/def ::tokens (s/coll-of ::token))

(defn- calc-selected-height
  [orig-sheight tokens formats context]
  (let [height-increase-tokens (filter p/horizontal-increase? tokens)
        total-increase (reduce + 0 (map
                                     #(p/height % formats context)
                                     height-increase-tokens))]
    (let [res (+ orig-sheight total-increase)]
      res)))
(s/fdef calc-selected-height
  :args (s/cat :orig-swidth number?
               :tokens ::tokens
               :formats map?
               :context map?)
  :ret number?)

(defn calc-selected-width
  [orig-swidth tokens formats context]
  (let [d-width (reduce + 0 (map
                              #(p/width % formats context)
                              tokens))]
    (if (some p/horizontal-increase? tokens)
      d-width
      (+ orig-swidth d-width))))
(s/fdef calc-selected-width
  :args (s/cat :orig-swidth number?
               :tokens ::tokens
               :formats map?
               :context map?)
  :ret number?)

(defn split-tokens
  [tokens {:as hole-dimensions :keys [hheight hwidth]} formats context]
  (let [first-token (first tokens)
        init-state {:selected []
                    :remaining tokens
                    :selected-height (if first-token (p/height first-token formats context) 0)
                    :selected-width 0}]
    (loop [{:as acc :keys [selected remaining selected-height selected-width]} init-state]
      (let [remaining-space {:width (- hwidth selected-width)
                             :height (- hheight selected-height)}]
        (let [[selected* tokens remaining*] (p/select
                                              (first remaining)
                                              [selected (drop 1 remaining)]
                                              remaining-space
                                              formats
                                              context)]
          (if tokens
            (recur (-> acc
                       (assoc-in [:selected] (into selected* tokens))
                       (assoc-in [:remaining] remaining*)
                       (update-in [:selected-width] calc-selected-width tokens formats context)
                       (update-in [:selected-height] calc-selected-height tokens formats context)))
            {:selected selected*
             :remaining remaining*}))))))
(s/def ::hheight number?)
(s/def ::hwidth number?)
(s/def ::selected ::tokens)
(s/def ::remaining ::tokens)
(s/fdef split-tokens
  :args (s/cat :tokens ::tokens
               :kwargs (s/keys :req-un [::hheight ::hwidth])
               :formats map?
               :context map?)
  :ret (s/keys :req-un [::selected ::remaining]))

;; 4b. Add those tokens to the page in that hole (use the template as the base)

(defn- page-template-exists?
  "Trying to stamp a page that requests a template not in the context
  is an error. This is function is used to give a clear name to the
  precondition of `get-template`."
  [page-data context]
  (get-in context [:templates (:template page-data)]))

(defn- get-template
  [data context]
  (assert (page-template-exists? data context)
          (str "No template " (:template data) " for page."))
  (:template data))

(defn- hole-descriptor
  "Get the hole-descriptor of hole in template."
  [template hole context]
  (first
    (filter (comp (partial = hole) :name)
            (context/template-holes-any template context))))

(defmulti process-hole
  "How to process a hole depends on its type, but the result will always be a
  pair of [overflow, current-page]. On most holes, overflow will be the same as
  the data that comes in; the option to make it different is present for cases
  such as parsed-text holes, where a longer text can span multiple pages."
  (fn [template [hole _] context]
    (:type (hole-descriptor template hole context))))

(defmethod process-hole :default
  [template [hole data] context]
  [(hash-map hole data)
   (hash-map hole {:contents nil})])

(defmethod process-hole :image
  [template [hole {:as data :keys [contents]}] context]
  [(hash-map hole data)
   (hash-map hole {:contents contents})])

(defmethod process-hole :text
  [template [hole {:as data :keys [contents]}] context]
  [(hash-map hole data)
   (hash-map hole data)])

(defmethod process-hole :text-parsed
  [template [hole {:as data :keys [contents]}] context]
  (let [{:keys [height width format]} (hole-descriptor template hole context)
        tokens (if (:tokenized? (meta (:text contents)))
                 (:text contents)
                 (tokenize (xml/parse-str (:text contents))))
        {:keys [selected remaining]} (split-tokens
                                       tokens
                                       {:hheight height :hwidth width}
                                       format
                                       context)]
    [(hash-map hole (assoc-in data [:contents :text] ((fnil vary-meta []) remaining assoc :tokenized? true)))
     (hash-map hole {:contents selected})]))

(defn- contains-parsed-text-holes?
  "Does data contain contents for any holes that are of the type :text-parsed?"
  [data template-holes]
  (let [hole-types (into {} (map (juxt :name :type) template-holes))]
    (some #{:text-parsed}
          (map #(get hole-types %) (into []
                                         (comp
                                           (filter (comp seq :text :contents second))
                                           (map first))
                                         data)))))

(comment
  (def data {:text {:contents {:text '("A" "B")}}
             :not-text {:contents {:image "an image buffer"}}})
  (def data' (assoc-in data [:text :contents :text] '()))
  (def template-holes [{:name :text :type :text-parsed}
                       {:name :non-text :type :image}])
  (contains-parsed-text-holes? data template-holes) ;=> :text-parsed
  (contains-parsed-text-holes? data' template-holes) ;=> nil
  )

(defn data->pages
  "Convert a single map describing a number of pages to a vector of maps
  describing the individual pages in detail. The output pages are ready to be
  stamped onto PDFs.

  The input data must have the following keys:

  - :template - Which template to use for this data
  - :locations - Vector. What to put on the individual holes of the template

  The output has the same structure."
  [data context]
  (loop [pages []
         d data]
    (let [template-name (:template d) ;; 2.

          ;; 3. - 5.
          processed-holes (map #(process-hole template-name % context) (:locations d))

          current-page {:template template-name
                        :locations (apply merge (map second processed-holes))}

          overflow-template (context/get-template-overflow template-name context) ;; 6.
          overflow {:template overflow-template
                    :locations (apply merge (map first processed-holes))}]
      (if (and overflow-template
               (contains-parsed-text-holes?
                 (:locations overflow)
                 (context/template-holes-any template-name context)))
        (recur ;; 7.
               (conj pages current-page)
               overflow)
        (conj pages current-page)))))

