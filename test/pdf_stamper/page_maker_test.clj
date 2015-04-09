(ns pdf-stamper.page-maker-test
  (:require
    [clojure.test :refer :all]
    [clojure.test.check :as tc]
    [clojure.test.check.properties :as prop]
    [clojure.test.check.generators :as gen]
    [clojure.test.check.clojure-test :refer [defspec]]

    [pdf-stamper.test-generators :as pdf-gen]

    [pdf-stamper.page-maker :as pm]
    [pdf-stamper.protocols :as p]
    [pdf-stamper.tokenizer.tokens :as t]
    [pdf-stamper.context :as context]))

(def splitting-tokens-honours-max-line-width-prop
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

          ;; Used for testing the property:
          parts (partition-by type selected)
          words (map
                  #(filter
                     (fn [token] (instance? pdf_stamper.tokenizer.tokens.Word token))
                     %)
                  parts)
          line-width (fn [tokens]
                       (reduce + 0 (map #(p/width % formats context) tokens)))
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

(def splitting-tokens-honours-max-height-prop
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

          ;; Used for testing the property:
          parts (partition-by type selected)
          new-lines (mapcat
                      #(filter
                         (fn [token] (instance? pdf_stamper.tokenizer.tokens.NewLine token))
                         %)
                      parts)
          line-height (if (first selected)
                        (p/height (first selected) formats context)
                        0)
          lines-height (* line-height (inc (count new-lines)))]
      (<= lines-height (:hheight hole-dimensions)))))

(defspec splitting-tokens-honours-max-line-width
  100
  splitting-tokens-honours-max-line-width-prop)

(defspec splitting-tokens-honours-max-height
  100
  splitting-tokens-honours-max-height-prop)

