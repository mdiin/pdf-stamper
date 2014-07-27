(ns pdt.pdf.text.fonts
  (:require
    [clojure.string :as string])
  (:import
    [org.apache.pdfbox.pdmodel.font PDType1Font PDTrueTypeFont]))

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

(defn add-font
  [doc in-stream font style fonts]
  (let [ttf (PDTrueTypeFont/loadTTF doc in-stream)]
    (assoc-in fonts [(keyword font) style] ttf)))

(defn get-font
  [font-name style context]
  (get-in context [:fonts font-name style]
          (get-in context [:fonts :times style]
                  (get-in context [:fonts :times #{:regular}]))))

