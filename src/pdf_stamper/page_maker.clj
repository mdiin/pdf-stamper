(ns pdf-stamper.page-maker
  (:require
    [clojure.data.xml :as xml]

    [pdf-stamper.context :as context]
    [pdf-stamper.protocols :as p :refer [SelectToken]]
    [pdf-stamper.tokenizer :refer [tokenize]]
    [pdf-stamper.tokenizer.xml :as xml-tokenizer]
    [pdf-stamper.tokenizer.tokens :as t]))

;; The general algorithm for building pages:
;;
;; 0. Tokenize the `:parsed-text` for all pieces of data
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

;; Build data:
;;
;; 1. Tokenize all text holes in all pieces of data
;;

(def test-data
  {:locations {:a {:contents {:image nil}}
               :b {:contents {:text "<bbb><p>abc</p></bbb>"}}}})

(defn- tokenize-xml-contents
  [location]
  (let [[loc-name content-map] location]
    (if-let [xml-str (get-in content-map [:contents :text])]
      {loc-name (update-in content-map [:contents :text] (comp flatten tokenize xml/parse-str))}
      {loc-name content-map})))

(defn- process-one-data
  [data]
  (update-in data [:locations] #(into {} (map tokenize-xml-contents (seq %)))))

(defn- process-all-data
  [ds]
  (map process-one-data ds))

;; Fill holes in page, respecting space constraints
;;
;; 3-4c
;;

;; 4a. Take the number of tokens for which there is room in the hole
(extend-protocol p/SelectToken
  pdf_stamper.tokenizer.tokens.Word
  (select-token [token {:as remaining-space :keys [width height]} formats context]
    (let [res (cond
            ;; Room for one more on the line
            (and
              (<= (p/width token formats context) width)
              (<= (p/height token formats context) height))
            [token]

            ;; No more room on line, but room for one more line
            (and
              (> (p/width token formats context) width)
              (<= (* (p/height token formats context) 2) height))
            [(t/->NewLine (:style token)) token]

            :default
            nil)]
      res))

  (horizontal-increase? [_] false)

  pdf_stamper.tokenizer.tokens.ListBullet
  (select-token [token {:as remaining-space :keys [width height]} formats context]
    (cond
      ;; No more room on line, issue warning
      (<= (p/width token formats context) width)
      (do
        (println "WARNING: Hole for bullet paragraph narrower than bullet character!")
        [token])

      :default
      [token]
      ))

  (horizontal-increase? [_] false)

  pdf_stamper.tokenizer.tokens.ListNumber
  (select-token [token {:as remaining-space :keys [width height]} formats context]
    (cond
      ;; No more room on line, issue warning
      (<= (p/width token formats context) width)
      (do
        (println "WARNING: Hole for number paragraph narrower than number!")
        [token])

      :default
      [token]))

  (horizontal-increase? [_] false)

  pdf_stamper.tokenizer.tokens.NewLine
  (select-token [token {:as remaining-space :keys [width height]} formats context]
    (cond
      ;; Room for one more line
      (<= (p/height token formats context) height)
      [token]

      :default
      nil))

  (horizontal-increase? [_] true)

  pdf_stamper.tokenizer.tokens.ParagraphBegin
  (select-token [token {:as remaining-space :keys [width height]} formats context]
    (cond
      (<= (p/height token formats context) height)
      [token]

      :default
      nil))

  (horizontal-increase? [_] true)

  pdf_stamper.tokenizer.tokens.ParagraphEnd
  (select-token [token {:as remaining-space :keys [width height]} formats context]
    (cond
      (<= (p/height token formats context) height)
      [token]

      :default
      nil))

  (horizontal-increase? [_] true)

  pdf_stamper.tokenizer.tokens.NewParagraph
  (select-token [token {:as remaining-space :keys [width height]} formats context]
    (cond
      ;; Room for a new paragraph
      (<= (p/height token formats context) height)
      [token]

      :default
      nil))

  (horizontal-increase? [_] true)

  pdf_stamper.tokenizer.tokens.NewPage
  (select-token [token {:as remaining-space :keys [width height]} formats context]
    ;; Always room for a page break...
    [token])

  (horizontal-increase? [_] true)

  nil
  (select-token [_ _ _ _] nil)
  (horizontal-increase? [_] false))

(defn- maybe-inc-height
  [orig-sheight tokens formats context]
  {:post [(number? %)]}
  (let [height-increase-tokens (filter p/horizontal-increase? tokens)
        total-increase (reduce + 0 (map
                                     #(p/height % formats context)
                                     height-increase-tokens))]
    (let [res (+ orig-sheight total-increase)]
      res)))

(defn- all-tokens
  [tokens]
  (reduce
    #(and %1 %2)
    true
    (map #(satisfies? p/SelectToken %) tokens)))

(defn split-tokens
  [tokens {:as hole-dimensions :keys [hheight hwidth]} formats context]
  {:pre [(number? hheight)
         (number? hwidth)]
   :post [(all-tokens (:selected %))
          (all-tokens (:remaining %))]}
  (let [first-token (first tokens)
        init-state {:selected []
                    :remaining tokens
                    :selected-height (if first-token (p/height first-token formats context) 0)
                    :selected-width 0}]
    (loop [{:as acc :keys [selected remaining selected-height selected-width]} init-state]
      (let [remaining-space {:width (- hwidth selected-width)
                             :height (- hheight selected-height)}]
        (if-let [tokens (p/select-token (first remaining) remaining-space formats context)]
          (recur (-> acc
                     (update-in [:selected] into tokens)
                     (update-in [:remaining] (partial drop 1))
                     (update-in [:selected-width] + (p/width (first remaining) formats context))
                     (update-in [:selected-height] maybe-inc-height tokens formats context)))
          (select-keys acc [:selected :remaining]))))))

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

(defmulti process-hole (fn [template [hole _] context]
                         (get-in template [hole :type])))

(defmethod process-hole :default
  [template [hole data] context]
  [data
   (assoc-in template [hole :contents] nil)])

(defn- dimensions
  "Get the dimensions of hole in template. The dimensions cannot vary across
  even and odd pages."
  [template hole context]
  (letfn [(template-hole
            [side]
            (first
              (filter (comp (partial = hole) :name)
                      (context/template-holes template side context))))]
    (when-let [hole-spec (or (template-hole :even) (template-hole :odd))]
      (select-keys hole-spec [:height :width]))))

(defmethod process-hole :parsed-text
  [template [hole {:as data :keys [contents]}] context]
  (let [{:keys [height width]} (dimensions template hole context)
        tokens (tokenize (:text contents))
        {:keys [selected remaining]} nil]
    [new-data
     (assoc-in template [hole :contents] tokens)]))

(defn- contains-parsed-text-holes?
  [data template]
  (some #{:parsed-text}
        (map #(get-in template [% :type])
             (keys (:locations data)))))

(defn data->pages
  [data context]
  (loop [pages []
         template (:template data)
         d data]
    (let [processed-holes (map #(process-hole template % context) (:locations d))

          ;; overflow: same format as data; {:locations {:loc-a {:content {:text ...}} :loc-c {:content {:image ...}}}}
          overflow (apply merge (map first processed-holes))

          ;; current-page: same format as template
          current-page (apply merge (map second processed-holes))
          overflow-template (context/get-template-overflow template context)]
      (if (and (contains-parsed-text-holes? overflow template) overflow-template)
        (recur
          (conj pages current-page)
          overflow-template
          overflow)
        (conj pages current-page)))))

;; 4c. Drop those tokens from the data set

;; 5. Repeat 3-4c for the remaining holes on the selected template

;; 6. Choose a new template based on the `:overflow` key

;; 7.Repeat 3-6 for the remaining data

