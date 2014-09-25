(ns pdf-stamper.manual.image-quality
  (:require
    [pdf-stamper :refer :all]
    [clojure.edn :as edn]))

(def template-1 (edn/read-string (slurp "test/templates/image_quality/template-1.edn")))
(def template-pdf-1 "test/templates/image_quality/template-1.pdf")
(def image-1 (javax.imageio.ImageIO/read (clojure.java.io/as-file "test/templates/image_quality/image.png")))

(def context (->> base-context
                  (add-template template-1 template-pdf-1)
                  ))

(def pages
  [{:template :template-1
    :locations {:one {:contents {:image image-1}}}}
   {:template :template-1
    :locations {:two {:contents {:image image-1}}}}
   {:template :template-1
    :locations {:three {:contents {:image image-1}}}}])

(def out (fill-pages pages context))
(.writeTo out (java.io.FileOutputStream. "out/image-quality.pdf"))
(.close out)

