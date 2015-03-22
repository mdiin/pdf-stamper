(ns pdf-stamper.test-generators
  (:require
    [clojure.test.check :as tc]
    [clojure.test.check.generators :as gen]
    [clojure.test.check.properties :as prop]
    
    [pdf-stamper.tokenizer.tokens :as t]))

;; TODO:
;; 1. What is a valid style map?
;; 2. Generate valid style maps
;; 3. Generate sequences of word tokens with special tokens interleaved
;; 4. What is a valid data piece?
;; 5. Generate valid data pieces
;; 6. What is a valid template description?
;; 7. Generate valid template descriptions
;;

(def word-token-gen
  (gen/fmap (partial apply t/t-word)
            (gen/tuple (gen/vector gen/string-alphanumeric)
                       (gen/not-empty gen/string-alphanumeric))))

