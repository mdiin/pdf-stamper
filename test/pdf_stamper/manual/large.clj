(ns pdf-stamper.manual.large
  (:require
    [pdf-stamper :refer :all]
    [clojure.edn :as edn]))

(def template-1 (edn/read-string (slurp "test/templates/large/template-1.edn")))
(def template-2 (edn/read-string (slurp "test/templates/large/template-2.edn")))
(def template-pdf-1 "test/templates/large/template-1.pdf")
(def template-pdf-2 "test/templates/large/template-2.pdf")

(def font-1 "test/templates/large/OpenSans-Regular.ttf")
(def font-2 "test/templates/large/OpenSans-Bold.ttf")
(def font-3 "test/templates/large/OpenSans-Semibold.ttf")

(def image-1 (javax.imageio.ImageIO/read (clojure.java.io/as-file "test/templates/large/image-1.jpg")))
(def text-1
  "<pp><h1>Banebeskrivelse</h1><p>Ham aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa<em><strong>hock</strong> ullamco</em> quis, t-bone biltong kielbasa sirloin prosciutto non <b>ribeye</b> andouille chuck mollit.</p><h2>Regler</h2><p>Sausage commodo ex cupidatat in pork loin. Ham leberkas sint pork chop bacon. <em><b>Chuck ea dolor</b></em>, salami sausage ad duis tongue officia nisi veniam pork belly cupidatat.</p><p>test number two</p><h3>Huskeliste</h3><ul><li>one time <b>ape</b> and duck</li><li>two times <em><b>ape</b></em> and duck</li><li>abekat er en led hest, med mange gode grise i stalden. Test</li></ul></pp>")

(def text-2
  "<pp><p>Abekat <em>er <b>en</b> ged</em></p></pp>")

(def context (->> base-context
                  (add-font font-1 :open-sans #{:regular})
                  (add-font font-2 :open-sans #{:bold})
                  (add-font font-3 :open-sans #{:semi-bold})
                  (add-template template-1 template-pdf-1)
                  (add-template template-2 template-pdf-1)))

(def pages
  [{:template :template-1
    :locations {:one {:contents {:image image-1}}
                :two {:contents {:text text-1}}}}

   {:template :template-2
    :locations {:one {:contents {:text text-2}}
                :two {:contents {:text "Monkey balls!"}}}}])

(def out (fill-pages pages context))
(.writeTo out (java.io.FileOutputStream. "out/large.pdf"))
(.close out)

