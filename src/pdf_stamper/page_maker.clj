(ns pdf-stamper.page-maker
  (:require
    [clojure.data.xml :as xml]
    [clojure.test.check :as tc]
    [clojure.test.check.generators :as gen]
    [clojure.test.check.properties :as prop]
    
    [pdf-stamper.tokenizer :refer [tokenize]]
    [pdf-stamper.tokenizer.xml :as xml-tokenizer]))

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

