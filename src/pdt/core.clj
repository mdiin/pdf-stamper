(ns pdt.core
  (:require
    [pdt.pdf :as pdf]
    [pdt.json :as json]))

(defn build-pdf
  "Data input format:
  
  {:style :a-style
   :pages [{:kind :a-kind
            :data {:a-location \"text|URL\"
                   ...}}]}
  
  :a-location should match a name in a JSON template
  description."
  [data]
  (let [style (name (:style data))
        pages (:pages data)]
    (with-open [document pdf/new-document]
      (reduce (fn [document page]
                (let [template-page (pdf/load-template style (:kind page))
                      template-json (json/load-template style (:kind page))]
                  (.. document addPage template-page)
                  (pdf/fill-page document template-page template-json page)))
              document
              pages))))

