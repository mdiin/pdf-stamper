;; Although data input to pdf-stamper is called *pages*, that is not strictly true. They might contain more text than is
;; possible to write in the space given by the requested template, or they might have other placements requirements.
;;
;; The functions in this namespace transform the input "pages" such that each data page directly corresponds to a single page
;; in the final PDF.
(ns pdf-stamper.pages
  (:require
    [pdf-stamper.context :as context]
    [pdf-stamper.text.parsed :as text-parsed]
    [pdf-stamper.pages.text-wrap :as wrap]))

(defn- wrappable-hole?
  [hole]
  (= (:type hole) :text-parsed))

(defn- wrap-hole
  [wrappable page context]
  (let [location-paragraphs (text-parsed/get-paragraph-nodes
                              (get-in page [:locations (:name wrappable)]))]
    (wrap/explode-text-location (:template page)
                                wrappable
                                location-paragraphs
                                context)))

(defn- page-template-exists?
  "Trying to stamp a page that requests a template not in the context
  is an error. This is function is used to give a clear name to the
  precondition of `fill-page`."
  [page-data context]
  (get-in context [:templates (:template page-data)]))

(defn- explode-page
  [page context]
  (assert (page-template-exists? page context)
          (str "No template " (:template page) " for page."))
  (let [template-holes (context/get-template-holes (:template page) context)
        wrappable-holes (filter wrappable-hole? template-holes)
        wrapped-holes (map #(wrap-hole % page context) wrappable-holes)]
    nil)
  (vector page))

(defn explode-pages
  "Given a seq of data pages transforms it to a seq of pages in
  direct correspondence to PDF pages; i.e. it handles text overflow,
  and any other special requirements."
  [pages context]
  (mapcat #(explode-page % context) pages))

