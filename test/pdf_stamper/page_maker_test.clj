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
    [tokens (pdf-gen/text-elements [:paragraph :bullet])
     hole-dimensions (gen/fmap (partial into {})
                               (gen/tuple
                                 (gen/tuple
                                   (gen/return :hheight) (gen/choose 1 1000))
                                 (gen/tuple
                                   (gen/return :hwidth) (gen/choose 1 1000))))
     line-spacing pdf-gen/spacing
     paragraph-spacing pdf-gen/spacing
     indent pdf-gen/indent]
    (let [formats {:paragraph {:font :times
                               :size 12
                               :spacing {:line line-spacing
                                         :paragraph paragraph-spacing}
                               :indent indent}}
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
    [tokens (pdf-gen/text-elements [:paragraph])
     hole-dimensions (gen/fmap (partial into {})
                               (gen/tuple
                                 (gen/tuple
                                   (gen/return :hheight) (gen/choose 1 1000))
                                 (gen/tuple
                                   (gen/return :hwidth) (gen/choose 1 1000))))
     line-spacing pdf-gen/spacing
     paragraph-spacing pdf-gen/spacing
     indent pdf-gen/indent]
    (let [formats {:paragraph {:font :times
                               :size 12
                               :spacing {:line line-spacing
                                         :paragraph paragraph-spacing}
                               :indent indent}}
          context context/base-context
          {:keys [selected remaining]} (pm/split-tokens
                                         tokens
                                         hole-dimensions
                                         formats
                                         context)

          ;; Used for testing the property:
          parts (partition-by type selected)
          horizontal-increases (mapcat #(filter p/horizontal-increase? %) parts)
          first-token-height (if (first selected)
                               (p/height (first selected) formats context)
                               0)
          lines-height (reduce
                         (fn [acc token]
                           (+ acc (p/height token formats context)))
                         first-token-height
                         horizontal-increases)
          ]
      ;; Double/POSITIVE_INFINITY means that the hole is filled
      ;; entirely, e.g. from a page break.
      (or (= Double/POSITIVE_INFINITY lines-height)
          (<= lines-height (:hheight hole-dimensions)))
      )))

(defspec splitting-tokens-honours-max-line-width
  100
  splitting-tokens-honours-max-line-width-prop)

(defspec splitting-tokens-honours-max-height
  100
  splitting-tokens-honours-max-height-prop)

