;; The state of pdf-stamper is encapsulated in a datastructure called the context. This structure contains
;; fonts and templates, and is partly constructed by users of pdf-stamper by adding to a base context.
;; This base context contains the fonts included in PDFBox standard, and nothing else.
;;
;; The functions in this namespace all exist to modify or query the context datastructure.

(ns pdf-stamper.context
  (:require
    [clojure.edn :as edn]
    [clojure.string :as string])
  (:import
    [org.apache.pdfbox.pdmodel PDDocument]
    [org.apache.pdfbox.pdmodel.font PDFont PDType1Font PDTrueTypeFont]))

;; ## Templates

(def base-templates
  "There are no standard templates in pdf-stamper."
  {})

(defn add-template
  "Adding templates to the context is achieved using this function.
  When adding a template two things are needed: The template description,
  i.e. what goes where, and a locator for the PDF page to use with the
  template description. The template locator can be either a URL or a string."
  [template-def template-uri context]
  (if template-def
    (-> context
        (assoc-in [:templates (:name template-def)] template-def)
        (assoc-in [:templates (:name template-def) :uri] template-uri))
    context))

(defn get-template-document
  "The template file is loaded lazily, i.e. it is not until a page actually
  requests to be written using the added template that it is read to memory."
  [template context]
  (let [file-uri (get-in context [:templates template :uri])]
    (PDDocument/load file-uri)))

(defn get-template-holes
  "Any template consists of a number of holes specifying the size and shape
  of data when stamped onto the template PDF page."
  [template context]
  (get-in context [:templates template :holes]))

(defn get-template-overflow
  "Templates can specify an overflow template, a template that will be used
  for any data that did not fit in the holes on the original template's page."
  [template context]
  (get-in context [:templates template :overflow]))

;; ## Fonts

(def base-fonts
  "pdf-templates uses PDFBox under the hood, and because of that includes
  all the standard PDF fonts defined by PDFBox."
  {:times {#{:regular} PDType1Font/TIMES_ROMAN
           #{:bold} PDType1Font/TIMES_BOLD
           #{:italic} PDType1Font/TIMES_ITALIC
           #{:bold :italic} PDType1Font/TIMES_BOLD_ITALIC}
   :helvetica {#{:regular} PDType1Font/HELVETICA
               #{:bold} PDType1Font/HELVETICA_BOLD
               #{:italic} PDType1Font/HELVETICA_OBLIQUE
               #{:bold :italic} PDType1Font/HELVETICA_BOLD_OBLIQUE}
   :courier {#{:regular} PDType1Font/COURIER
             #{:bold} PDType1Font/COURIER_BOLD
             #{:italic} PDType1Font/COURIER_OBLIQUE
             #{:bold :italic} PDType1Font/COURIER_BOLD_OBLIQUE}
   :symbol {#{:regular} PDType1Font/SYMBOL}
   :zapf-dingbats {#{:regular} PDType1Font/ZAPF_DINGBATS}})

(defn add-font
  "If any templates have need of fonts that are not part of the standard
  PDF font library, they can be added by providing a font descriptor and
  the font name and style. As an example, had the Times New Roman bold font
  not been present already, here is how one would add it:
  `(add-font \"times_bold.ttf\" :times #{:bold})`.

  Notice how the style is a set of keywords. This is to support the combined
  font styles like bold AND italic, without requiring an arbitrary ordering
  on the individual parts of the style.

  In the example above the font descriptor was provided as a string representing
  a file name, but it could just as well have been a `java.io.InputStream`.

  *Note*: Only TTF fonts are supported."
  [desc font style context]
  (-> context
      (assoc-in [:fonts (keyword font) style :desc] desc)
      (update-in [:fonts-to-embed] #((fnil conj []) % [(keyword font) style]))))

(defn get-font
  [font-name style context]
  {:post [(instance? PDFont %)]}
  (get-in context [:fonts font-name style]
          (get-in context [:fonts :times style]
                  (get-in context [:fonts :times #{:regular}]))))

(defn embed-font
  [doc font style context]
  (if-let [font-desc (get-in context [:fonts font style :desc])]
    (assoc-in context [:fonts font style] (PDTrueTypeFont/loadTTF doc font-desc))
    context))

(defn get-average-font-width
  [font-name style size context]
  (let [font (get-font font-name style context)]
    (* (/ (.. font (getAverageFontWidth)) 1000) size)))

(defn get-font-height
  [font-name style size context]
  (let [font (get-font font-name style context)]
    (* (/ (.. font (getFontDescriptor) (getFontBoundingBox) (getHeight)) 1000) size)))

(defn get-font-string-width
  [font-name style size string context]
  (let [font (get-font font-name style context)]
    (* (/ (.. font (getStringWidth string)) 1000) size)))

(def base-context
  {:templates base-templates
   :fonts base-fonts})

