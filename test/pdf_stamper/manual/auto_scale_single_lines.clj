;; This test is designed to show if multi-line texts stay properly inside the bounds given in the templates.
;;
;; An image with solid colour is placed as the background of the text holes, such that we can visually spot whether the
;; text fits within the box.
(ns pdf-stamper.manual.auto-scale-single-lines
  (:require
    [pdf-stamper :refer :all]
    [clojure.edn :as edn]))

(def template-1 (edn/read-string (slurp "test/templates/auto_scale_single_lines/template-1.edn")))
(def template-pdf-1 "test/templates/auto_scale_single_lines/template-1.pdf")

(def background (javax.imageio.ImageIO/read (clojure.java.io/as-file "test/templates/auto_scale_single_lines/background.png")))
(def text-text1 "This is a single line that is long")
(def text-text2 "Line")

(def context (->> base-context
                  (add-template template-1 template-pdf-1)))

(def pages
  [{:template :template-1
    :locations {:image-text-scaled {:contents {:image background}}
                :image-text-reference {:contents {:image background}}
                :text-text-scaled {:contents {:text text-text1}}
                :text-reference-size {:contents {:text text-text2}}}}])

(def out (fill-pages pages context))
(.writeTo out (java.io.FileOutputStream. "out/auto_scale_single_lines.pdf"))
(.close out)

