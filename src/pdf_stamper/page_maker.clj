(ns pdf-stamper.page-maker
  (:require
    [clojure.data.xml :as xml]
    [clojure.test.check :as tc]
    [clojure.test.check.generators :as gen]
    [clojure.test.check.properties :as prop]
    
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

(defmulti select-token (fn [{:keys [remaining]}] (:kind (first remaining))))

(defmethod select-token :pdf-stamper.tokenizer.tokens/word
  [{:as acc :keys [selected remaining sheight swidth]} {:keys [hheight hwidth]} formats]
  (cond
    ;; There is room for another token on the line
    (and
      (<= (+ swidth (t/width token formats context)) hwidth)
      (<= (+ sheight (t/height token formats context)) hheight))
    {:recur true
     :acc (-> acc
              (update-in [:swidth] + (t/width token formats context))
              (update-in [:selected] conj token)
              (update-in [:remaining] (partial drop 1)))}

    ;; There is no room on the line but room for another line
    (and
      (> (+ swidth (t/width token formats context)) hwidth)
      (<= (+ sheight (* (t/height token formats context) 2)) hheight))
    {:recur true
     :acc (-> acc
              (update-in [:sheight] + (t/height token formats context))
              (update-in [:swidth] (constantly (t/width token formats context)))
              (update-in [:selected] conj (t/t-new-line (:style token)))
              (update-in [:selected] conj token)
              (update-in [:remaining] (partial drop 1)))}

    ;; There is neither room for the token on the line nor for another line
    :default
    {:recur false
     :acc (select-keys acc [:selected :remaining])}))

(defn- split-tokens
  [tokens {:as dimensions :keys [hheight hwidth]} formats context]
  (let [init-state {:selected []
                    :remaining tokens
                    :sheight 0
                    :swidth 0}]
    (loop [{:as acc :keys [selected remaining sheight swidth]} init-state]
      (if-let [token (first remaining)]
        (cond
          ;; There is room for another token on the line
          (and
            (<= (+ swidth (t/width token formats context)) hwidth)
            (<= (+ sheight (t/height token formats context)) hheight))
          (recur
            (-> acc
                (update-in [:swidth] + (t/width token formats context))
                (update-in [:selected] conj token)
                (update-in [:remaining] (partial drop 1))))

          ;; There is no room on the line but room for another line
          (and
            (> (+ swidth (t/width token formats context)) hwidth)
            (<= (+ sheight (* (t/height token formats context) 2)) hheight))
          (recur
            (-> acc
                (update-in [:sheight] + (t/height token formats context))
                (update-in [:swidth] (constantly (t/width token formats context)))
                (update-in [:selected] conj (t/t-new-line (:style token)))
                (update-in [:selected] conj token)
                (update-in [:remaining] (partial drop 1))))

          ;; There is neither room for the token on the line nor for another line
          :default
          (select-keys acc [:selected :remaining]))
        (select-keys acc [:selected :remaining])))))

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

