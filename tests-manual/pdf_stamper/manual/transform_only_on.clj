(ns pdf-stamper.manual.transform-only-on
  (:require
    [pdf-stamper :refer :all]
    [clojure.edn :as edn]))

(def template-1 (edn/read-string (slurp "test/templates/transform_only_on/template-1.edn")))
(def template-1-filler (edn/read-string (slurp "test/templates/transform_only_on/template-1-filler.edn")))
(def template-pdf-1 "test/templates/transform_only_on/template-1.pdf")

(def background (javax.imageio.ImageIO/read (clojure.java.io/as-file "test/templates/transform_only_on/background.png")))
(def text "Ålquiver")
(def filler-text "FIllER")

(def context (->> base-context
                  (add-template template-1 template-pdf-1)
                  (add-template template-1-filler template-pdf-1)))

(def pages
  [{:template :template-1
    :locations {:centered-image {:contents {:image background}}
                :centered-text {:contents {:text text}}
                :top-image {:contents {:image background}}
                :top-text {:contents {:text text}}
                :bottom-image {:contents {:image background}}
                :bottom-text {:contents {:text text}}}
    :filler-locations {:centered-text {:contents {:text filler-text}}}}
   {:template :template-1
    :locations {:centered-image {:contents {:image background}}
                :centered-text {:contents {:text text}}
                :top-image {:contents {:image background}}
                :top-text {:contents {:text text}}
                :bottom-image {:contents {:image background}}
                :bottom-text {:contents {:text text}}}
    :filler-locations {:centered-text {:contents {:text filler-text}}}}
   {:template :template-1
    :locations {:centered-image {:contents {:image background}}
                :centered-text {:contents {:text text}}
                :top-image {:contents {:image background}}
                :top-text {:contents {:text text}}
                :bottom-image {:contents {:image background}}
                :bottom-text {:contents {:text text}}}
    :filler-locations {:centered-text {:contents {:text filler-text}}}}])

(def out (fill-pages pages context))
(.writeTo out (java.io.FileOutputStream. "out/transform-only-on.pdf"))
(.close out)

