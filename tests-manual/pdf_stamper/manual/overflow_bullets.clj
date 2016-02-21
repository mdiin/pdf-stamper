;; This is a test designed to show overflowing of multi-line text holes, i.e. holes of type `:text-parsed`.
;; Specifically, this test is about breaking bullet points over multiple pages.
;;
;; An image with solid colour is placed as the background of the text holes, such that we can visually spot whether the
;; text fits within the box.
(ns pdf-stamper.manual.overflow-bullets
  (:require
    [pdf-stamper :refer :all]
    [clojure.edn :as edn]))

(def template-1 (edn/read-string (slurp "test/templates/overflow_bullets/template-1.edn")))
(def template-pdf-1 "test/templates/overflow_bullets/template-1.pdf")

(def background (javax.imageio.ImageIO/read (clojure.java.io/as-file "test/templates/overflow_bullets/background.png")))
(def text
  "<pp><p>Introductory paragraph of text spanning multiple lines.</p><ul><li>This is a bullet that is much longer than any bullets you would normally see and addtionally it is without stops or breaks making it even harder to read than it should be although bullets should never be very long in any case. It is even longer than you had imagined, and now it suddenly contains a comma!</li></ul><p>This is just to test that paragraph afterwards look correct.</p></pp>")

(def context (->> base-context
                  (add-template template-1 template-pdf-1)))

(def pages
  [{:template :template-1
    :locations {:background {:contents {:image background}}
                :text {:contents {:text text}}}}])

(def out (fill-pages pages context))
(.writeTo out (java.io.FileOutputStream. "out/overflow_bullets.pdf"))
(.close out)

