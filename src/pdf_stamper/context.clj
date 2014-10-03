;; The state of pdf-stamper is encapsulated in a datastructure called the context. This structure contains
;; fonts and templates, and is partly constructed by users of pdf-stamper by adding to a base context.
;; This base context contains the fonts included in PDFBox standard, and nothing else.
;;
;; The functions in this namespace all exist to modify or query the context datastructure. Functions relevant
;; to client code is exported in the `pdf-stamper` namespace, so this namespace is intended only for internal
;; use. However, the documentation may still be relevant to clients of pdf-stamper.

(ns pdf-stamper.context
  (:require
    [clojure.edn :as edn]
    [clojure.string :as string]
    [clojure.java.io :as io]
    [pdf-stamper.schemas :as schemas])
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
  ^{:pre [(some? template-uri)
          (schemas/valid-template? template-def)]}
  [template-def template-uri context]
  (-> context
      (assoc-in [:templates (:name template-def)] template-def)
      (assoc-in [:templates (:name template-def) :uri] template-uri)))

(defn get-template-document
  "The template file is loaded lazily, i.e. it is not until a page actually
  requests to be written using the added template that it is read to memory."
  [template context]
  (let [file-uri (get-in context [:templates template :uri])]
    (assert file-uri (str "file-uri is nil for template " template))
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
;;
;; Fonts in PDF follow the typographical conventions. Important font concepts for this project are:
;;
;; - *baseline*, the line that the cursor follows when writing
;; - *ascent*, the maximum ascent of any glyph above the baseline
;; - *descent*, the maximum descent of any glyph below the baseline
;;
;; These are illustrated below:
;;
;; ![Font explanation](images/font_explanation.png)
;;
;; When writing text the cursor origin is placed on the baseline.

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
  PDF font library, they can be added by providing a font descriptor,
  the font name and the font style. As an example, had the Times New Roman
  bold font not been present already, here is how one would add it:
  `(add-font \"times_bold.ttf\" :times #{:bold})`.

  Notice how the style is a set of keywords. This is to support the combined
  font styles like bold AND italic, without requiring an arbitrary ordering
  on the individual parts of the style.

  In the example above the font descriptor was provided as a string representing
  a file name, but it could just as well have been a `java.net.URL`, `java.net.URI`
  or `java.io.File`.

  In PDF, non-standard fonts should be embedded in the document that uses them.
  Adding a font like above does not automatically embed it to a document, since
  the context does not have knowledge of documents. Instead, the context is
  updated with a seq of [font style] pairs that need to be embedded when a new
  document is created.

  *Note*: Only TTF fonts are supported."
  [desc font style context]
  (-> context
      (assoc-in [:fonts (keyword font) style :desc] desc)
      (update-in [:fonts-to-embed] #((fnil conj []) % [(keyword font) style]))))

(defn embed-font
  "On creation of a new document all fonts in the seq of fonts to embed should
  be embedded. If for some reason a font is found in the seq of fonts to embed
  but does not contain a descriptor, nothing happens and the context is returned
  unmodified. In practice this situation is highly unlikely, and the check is
  primarily in place to prevent unanticipated crashes (in case code external to
  pdf-stamper modified the context).
  
  The font descriptor is coerced to an input stream and loaded into the document,
  after which it is automatically closed."
  [doc font style context]
  (if-let [font-desc (get-in context [:fonts font style :desc])]
    (assoc-in context [:fonts font style] (with-open [font (io/input-stream font-desc)]
                                            (PDTrueTypeFont/loadTTF doc font)))
    context))

(defn get-font
  "When a font has been added to the context and embedded in a document, it can
  be queried by providing the font name and style.
 
  It is guaranteed that a font is always found. Thus, if no font with the given
  name is registered the default font (Times New Roman) is used with the supplied
  style. If again no font is found, the default font and style are used (Times New
  Roman Regular)."
  [font-name style context]
  {:post [(instance? PDFont %)]}
  (get-in context [:fonts font-name style]
          (get-in context [:fonts :times style]
                  (get-in context [:fonts :times #{:regular}]))))

;; ### Font utilities
;;
;; The following utility functions rely on PDFBox' built-in font inspection methods. In PDFBox the font widths and heights
;; are returned in a size that is multiplied by 1000 (presumably because of rounding, but I may be wrong), which explains
;; the, otherwise seemingly arbitrary, divisions by 1000.

(defn get-average-font-width
  "Computing line lengths of unknown strings requires knowledge of the average
  width of a font, given style and size."
  [font-name style size context]
  (let [font (get-font font-name style context)]
    (* (/ (.. font (getAverageFontWidth)) 1000) size)))

(defn get-font-string-width
  "With complete knowledge of the string it is possible to get the exact width
  of the string."
  [font-name style size string context]
  (let [font (get-font font-name style context)]
    (* (/ (.. font (getStringWidth string)) 1000) size)))

(defn get-font-descent
  [font-name style size context]
  (let [font (get-font font-name style context)
        font-descriptor (.. font (getFontDescriptor))
        descent (.. font-descriptor (getDescent))]
    (* (/ (Math/abs descent) 1000) size)))

(defn get-font-ascent
  [font-name style size context]
  (let [font (get-font font-name style context)
        font-descriptor (.. font (getFontDescriptor))
        ascent (.. font-descriptor (getAscent))]
    (* (/ ascent 1000) size)))

(defn get-font-height
  "By adding the absolute value of the font's descent to the font's ascent,
  we get the actual height of the font. We have to use the absolute value
  of the descent since it might be a negative value (it probably is, at least
  for FreeType fonts)."
  [font-name style size context]
  (let [font (get-font font-name style context)
        font-descriptor (.. font (getFontDescriptor))
        ascent (.. font-descriptor (getAscent))
        descent (.. font-descriptor (getDescent))]
    (* (/ (+ ascent (Math/abs descent)) 1000) size)))

(defn get-font-leading
  "The leading is the extra spacing from baseline to baseline, used for
  multi-line text."
  [font-name style size context]
  (let [font (get-font font-name style context)
        font-descriptor (.. font (getFontDescriptor))
        leading (.. font-descriptor (getLeading))]
    (* (/ leading 1000) size)))

;; ## Base context

(def base-context
  "The base context is a combination of the base fonts with the base templates,
  and simply provides a good starting point for adding custom fonts and own
  templates."
  {:templates base-templates
   :fonts base-fonts})

