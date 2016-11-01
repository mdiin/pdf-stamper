(ns pdf-stamper.page-maker
  (:require
    [clojure.data.xml :as xml]
    [clojure.test.check :as tc]
    [clojure.test.check.generators :as gen]
    [clojure.test.check.properties :as prop]
    
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

(extend-protocol p/SelectToken
  pdf_stamper.tokenizer.tokens.Word
  (select-token [token {:as remaining-space :keys [width height]} formats context]
    (cond
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
      nil))

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

  pdf_stamper.tokenizer.tokens.NewParagraph
  (select-token [token {:as remaining-space :keys [width height]} formats context]
    (cond
      ;; Room for a new paragraph
      (<= (p/height token formats context) height)
      [token]
      
      :default
      nil))

  (horizontal-increase? [_] true)
  
  nil
  (select-token [_ _ _ _] nil)
  (horizontal-increase? [_] false))

(defn- maybe-inc-height
  [orig-sheight tokens formats context]
  (let [height-increase-tokens (filter p/horizontal-increase? tokens)
        total-increase (reduce + 0 (map
                                     #(p/height % formats context)
                                     height-increase-tokens))]
    (+ orig-sheight total-increase)))

(defn split-tokens
  [tokens {:as dimensions :keys [hheight hwidth]} formats context]
  (let [first-token (first tokens)
        init-state {:selected []
                    :remaining tokens
                    :sheight (if first-token (p/height first-token formats context) 0)
                    :swidth 0}]
    (loop [{:as acc :keys [selected remaining sheight swidth]} init-state]
      (let [remaining-space {:width (- hwidth swidth)
                             :height (- hheight sheight)}]
        (if-let [tokens (p/select-token (first remaining) remaining-space formats context)]
          (recur
            (-> acc
                (update-in [:selected] into tokens)
                (update-in [:remaining] (partial drop 1))
                (update-in [:swidth] + (p/width (first remaining) formats context))
                (update-in [:sheight] maybe-inc-height tokens formats context)))
          (select-keys acc [:selected :remaining]))))))

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

(defmulti process-hole (fn [template hole data context]
                         (get-in template [hole :type])))

(defmethod process-hole :default
  [template hole data context]
  [data
   (assoc-in template [hole :contents] nil)])

(defmethod process-hole :parsed-text
  [template hole data context]
  (let [tokens nil
        new-data nil]
    [new-data
     (assoc-in template [hole :contents] tokens)]))

