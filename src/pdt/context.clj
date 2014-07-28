(ns pdt.context
  (:require
    [clojure.edn :as edn]
    [clojure.string :as string])
  (:import
    [org.apache.pdfbox.pdmodel PDDocument]
    [org.apache.pdfbox.pdmodel.font PDType1Font PDTrueTypeFont]))

(def base-templates {})

(def base-fonts
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

(def base-context
  {:templates base-templates
   :fonts base-fonts})

(defn add-template
  "template-def: The definition, a map as described in the README.
  template-uri: locator for the PDF used by the template, URL or string.
  context: fonts, templates, etc.
  
  Add template to the context; does not open the associated URI."
  [template-def template-uri context]
  (if template-def
    (-> context
        (assoc-in [:templates (:name template-def)] template-def)
        (assoc-in [:templates (:name template-def) :uri] template-uri))
    context))

(defn get-template-document
  [template context]
  (let [file-uri (get-in context [:templates template :uri])]
    (PDDocument/load file-uri)))

(defn get-template-holes
  [template context]
  (get-in context [:templates template :holes]))

(defn add-font
  [doc in-stream font style context]
  (let [ttf (PDTrueTypeFont/loadTTF doc in-stream)]
    (assoc-in context [:fonts (keyword font) style] ttf)))

(defn get-font
  [font-name style context]
  (get-in context [:fonts font-name style]
          (get-in context [:fonts :times style]
                  (get-in context [:fonts :times #{:regular}]))))

