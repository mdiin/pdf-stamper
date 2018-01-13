;; This is a test designed to show overflowing of multi-line text holes, i.e. holes of type `:text-parsed`.
;;
;; An image with solid colour is placed as the background of the text holes, such that we can visually spot whether the
;; text fits within the box.
(ns pdf-stamper.manual.overflow
  (:require
    [pdf-stamper :refer :all]
    [clojure.edn :as edn]))

(def template-1 (edn/read-string (slurp "test/templates/overflow/template-1.edn")))
(def template-pdf-1 "test/templates/overflow/template-1.pdf")

(def background (javax.imageio.ImageIO/read (clojure.java.io/as-file "test/templates/overflow/background.png")))
(def text
  "<p><h1>Test</h1><p>This is a text that was written to test something about texts staying put within the given boundaries.</p><p>It was definitely not written to be read out load as it is missing all kinds of stops and pauses but it should look nice and a lot better than a random lorem ipsum.</p></p>")

(def context (->> base-context
                  (add-template template-1 template-pdf-1)))

(def pages
  [{:template :template-1
    :locations {:background {:contents {:image background}}
                :text {:contents {:text text}}}}])

(def out (fill-pages pages context))
(.writeTo out (java.io.FileOutputStream. "out/overflow.pdf"))
(.close out)

