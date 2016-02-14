(ns pdf-stamper.manual.lazyfonts
  (:require
    [pdf-stamper :refer :all]
    [clojure.edn :as edn]))

;; # Test showing font embedding
;;
;; This test is intended to show that only fonts that are actually used get embedded
;; in the document; i.e. that documents do not grow just because the context includes
;; a lot of fonts.
;;
;; It should be enough to load this file (in vim, `:e Reqiure!` if using vim-fireplace).
;; Output will be placed in the toplevel `out` directory as the file `lazyfonts.pdf`.
;;
;; *Note*: Fonts are not included in the repo due to potential licensing issues, so
;; feel free to add your own fonts.

(def template-1 (edn/read-string (slurp "test/templates/lazyfonts/template-1.edn")))
(def template-pdf-1 "test/templates/lazyfonts/template-1.pdf")

(def font-1 "test/templates/lazyfonts/OpenSans-Regular.ttf")
(def font-2 (java.io.File. "test/templates/lazyfonts/OpenSans-Bold.ttf"))
(def font-3 "test/templates/lazyfonts/OpenSans-Semibold.ttf")
(def font-4 "test/templates/lazyfonts/Monitorica-Bd.ttf")
(def font-5 "test/templates/lazyfonts/Monitorica-BdIt.ttf")
(def font-6 "test/templates/lazyfonts/Monitorica-It.ttf")
(def font-7 "test/templates/lazyfonts/Monitorica-Rg.ttf")
(def font-8 "test/templates/lazyfonts/PiratePower.ttf")
(def font-9 "test/templates/lazyfonts/Ricasso-Regular.ttf")

(def context (->> base-context
                  (add-font font-1 :open-sans #{:regular})
                  (add-font font-2 :open-sans #{:bold})
                  (add-font font-3 :open-sans #{:semibold})
                  (add-font font-4 :monitorica #{:bold})
                  (add-font font-5 :monitorica #{:bold :italic})
                  (add-font font-6 :monitorica #{:italic})
                  (add-font font-7 :monitorica #{:regular})
                  (add-font font-8 :pirate #{:regular})
                  (add-font font-9 :ricasso #{:regular})
                  (add-template template-1 template-pdf-1)))

(def pages
  [{:template :template-1
    :locations {:one {:contents {:text "Monkey balls!"}}
                :two {:contents {:text "Ape balls?"}}}}])

(def out (fill-pages pages context))
(.writeTo out (java.io.FileOutputStream. "out/lazyfonts.pdf"))
(.close out)

