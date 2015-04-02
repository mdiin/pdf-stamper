(ns pdf-stamper.core-test
  (:require
    [clojure.test :refer :all]
    [clojure.test.check :as tc]
    [clojure.test.check.properties :as prop]
    [clojure.test.check.generators :as gen]
    [clojure.test.check.clojure-test :refer [defspec]]

    [pdf-stamper.test-generators :as pdf-gen]
    
    [pdf-stamper.tokenizer.tokens :as t]
    [pdf-stamper.context :as context]
    [pdf-stamper.page-maker :as pm]))

(def split-tokens-contains-line-breaks-prop
  (prop/for-all
    [tokens (gen/vector (pdf-gen/token-word :paragraph))
     hole-dimensions (gen/fmap (partial into {})
                               (gen/tuple
                                 (gen/tuple
                                   (gen/return :hheight) (gen/choose 1 200))
                                 (gen/tuple
                                   (gen/return :hwidth) (gen/choose 1 200))))]
    (let [formats {:paragraph {:font :times
                               :size 12}}
          context context/base-context
          {:keys [selected remaining]} (pm/split-tokens
                                         tokens
                                         hole-dimensions
                                         formats
                                         context)
          parts (partition-by :kind selected)
          words (map
                  #(filter
                     (fn [t] (= (:kind t) :pdf-stamper.tokenizer.tokens/word))
                     %)
                  parts)
          line-width (fn [tokens]
                       (reduce + 0 (map #(t/width % formats context) tokens)))
          line-widths (map
                        #(vector (line-width %) (count %))
                        words)]
      (reduce
        #(and %1 %2)
        true
        (map #(if (= (second %) 1)
                true
                (<= (first %) (:hwidth hole-dimensions)))
             line-widths)))))

(defspec split-tokens-contains-line-breaks
  100
  split-tokens-contains-line-breaks-prop)

