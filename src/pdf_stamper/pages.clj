;; Although data input to pdf-stamper is called *pages*, that is not strictly true. They might contain more text than is
;; possible to write in the space given by the requested template, or they might have other placements requirements.
;;
;; The functions in this namespace transform the input "pages" such that each data page directly corresponds to a single page
;; in the final PDF.
(ns pdf-stamper.pages
  (:require
    [pdf-stamper.context :as context]
    [pdf-stamper.pages.text-wrap :as wrap]))

(defn- wrappable-hole?
  [hole]
  (= (:type hole) :text-parsed))

(defn- explode-page
  [page context]
  (assert (page-template-exists? page context)
          (str "No template " (:template page) " for page."))
  (let [template-holes (context/get-template-holes (:template page) context)
        wrappable-holes (filter wrappable-hole? template-holes)
        wrapped-holes (map #(wrap/hole % (get-in page [:locations (:name %)])) ;; [[ ... ] [ ... ] ...]
                           wrappable-holes)]
    nil)
  (vector page))

(defn explode-pages
  "Given a seq of data pages transforms it to a seq of pages in
  direct correspondence to PDF pages; i.e. it handles text overflow,
  and any other special requirements."
  [pages context]
  (mapcat #(explode-page % context) pages))

