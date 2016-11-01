(ns pdf-stamper.tokenizer.standard
  (:require
    [pdf-stamper.tokenizer :refer [Tokenizable tokenize]]
    [pdf-stamper.tokenizer.tokens :as token]))

;; ## Standard tokenizers
;;
;; These are important for base cases to the more involved tokenizers.

(defn- tokenize-str*
  [s style]
  (map (partial token/t-word style) (clojure.string/split s #" ")))

(extend-type java.lang.String
  Tokenizable
  
  (tokenize 
    ([s] (tokenize s nil))
    ([s style] (tokenize-str* s style))))

(defn- tokenize-seq*
  [s style]
  (map #(tokenize % style) s))

(extend-type clojure.lang.LazySeq
  Tokenizable
  
  (tokenize
    ([s] (tokenize s nil))
    ([s style] (tokenize-seq* s style))))

