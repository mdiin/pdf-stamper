;; This test is designed to show aligning single-line text holes, i.e. holes of type `:text`.
;;
;; An image with solid colour is placed as the background of the text holes, such that we can visually spot whether the aligned
;; text fits within the box.
(ns pdf-stamper.manual.align-single
  (:require
    [pdf-stamper :refer :all]
    [clojure.edn :as edn]))

(def template-1 (edn/read-string (slurp "test/templates/align_single/template-1.edn")))
(def template-pdf-1 "test/templates/align_single/template-1.pdf")

(def background (javax.imageio.ImageIO/read (clojure.java.io/as-file "test/templates/align_single/background.png")))
(def text "Ålquiver")

(def context (->> base-context
                  (add-template template-1 template-pdf-1)))

(def pages
  [{:template :template-1
    :locations {:centered-image {:contents {:image background}}
                :centered-text {:contents {:text text}}
                :top-image {:contents {:image background}}
                :top-text {:contents {:text text}}
                :bottom-image {:contents {:image background}}
                :bottom-text {:contents {:text text}}}}])

(def out (fill-pages pages context))
(.writeTo out (java.io.FileOutputStream. "out/align-single.pdf"))
(.close out)

