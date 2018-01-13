;; This test is designed to show if multi-line texts stay properly inside the bounds given in the templates.
;;
;; An image with solid colour is placed as the background of the text holes, such that we can visually spot whether the
;; text fits within the box.
(ns pdf-stamper.manual.text-field-height
  (:require
    [pdf-stamper :refer :all]
    [clojure.edn :as edn]))

(def template-1 (edn/read-string (slurp "test/templates/text_field_height/template-1.edn")))
(def template-pdf-1 "test/templates/text_field_height/template-1.pdf")

(def background (javax.imageio.ImageIO/read (clojure.java.io/as-file "test/templates/text_field_height/background.png")))
(def text-text
  "<p><p>This is a text that was written to test something about texts staying put within the given boundaries and was definitely not written to be read out load as it is missing all kinds of stops and pauses but it should look nice and a lot better than a random lorem ipsum.</p></p>")
(def text-list
  "<p><ul><li>boundaries and was definitely not written</li><li>ing about texts staying put</li><li>better than a random lorem</li><li>is a text that was</li><li>Five</li><li>Six</li></ul></p>")

(def context (->> base-context
                  (add-template template-1 template-pdf-1)))

(def pages
  [{:template :template-1
    :locations {:image-text {:contents {:image background}}
                :text-text {:contents {:text text-text}}
                :image-list {:contents {:image background}}
                :text-list {:contents {:text text-list}}}}])

(def out (fill-pages pages context))
(.writeTo out (java.io.FileOutputStream. "out/text-field-height.pdf"))
(.close out)

