(ns pdf-stamper.template-utils-test
  (:require
    [pdf-stamper.template-utils :refer :all]
    
    [clojure.test :as t :refer [deftest is testing]]
    [clojure.test.check.properties :refer [for-all]]
    [clojure.test.check.generators :as gen]
    [clojure.test.check.clojure-test :refer [defspec]]))

;; # Generators

;; ## Values
;;
;; Values are simple maps of anything, so we don't really care how they look
;; internally. All we care about is that they can be compared.

(def value-map (gen/map gen/keyword gen/keyword))

;; ## Non-variadic parts
;;
;; Represented internally as just arbitrary maps.

(def non-variadic-part value-map)

;; ## Variadic parts
;;
;; See namespace documentation for `pdf-stamper.template-utils` for a description
;; of their structure.

(def variant
  (gen/hash-map :pdf-stamper.template-utils/variant-name (gen/not-empty gen/string-alphanumeric)
                :pdf-stamper.template-utils/variant-part value-map))

(defn variadic-part
  [naming-scheme-parts]
  (if (not-empty naming-scheme-parts)
    (gen/hash-map :pdf-stamper.template-utils/name (gen/elements naming-scheme-parts)
                  :pdf-stamper.template-utils/optional? gen/boolean
                  :pdf-stamper.template-utils/variants (gen/not-empty
                                                         (gen/vector variant)))
    non-variadic-part))

(def parts
  (gen/let [naming-scheme-parts (gen/vector (gen/not-empty gen/string-alphanumeric))
            naming-scheme-base gen/string-alphanumeric
            naming-scheme (gen/return
                            (str naming-scheme-base (clojure.string/join
                                                      (map (fn [naming-scheme-part]
                                                             (str "$" naming-scheme-part "$"))
                                                           naming-scheme-parts))))
            ps (gen/vector-distinct-by (fn [elem]
                                         (if-let [variant-name (:pdf-stamper.template-utils/name elem)]
                                           variant-name
                                           elem))
                                       (gen/one-of [(variadic-part naming-scheme-parts) non-variadic-part]))]
    [naming-scheme ps]))

;; # Property helpers

(defn expected-number-of-templates
  [parts]
  (if (not-empty parts)
    (reduce (fn [expected part]
              (if-let [variants (:pdf-stamper.template-utils/variants part)]
                (* expected (if (:pdf-stamper.template-utils/optional? part)
                              (inc (count variants))
                              (count variants)))
                expected))
            1
            parts)
    0))

;; # Properties
;;
;; Generating parts is a heavy operation, so we have to limit the size of generated test output
;; to something reasonable. The default of 200 is too much, and a size of 8 should be enough
;; to try out a good amount of cases. Increasing this number beyond 8 will significantly increase
;; execution time of the tests.
;;
;; To compensate, we run through a larger number of tests than the default of 100 tests.

(defspec contains-expected-number-of-templates
  {:max-size 8
   :num-tests 1000}
  (for-all [[naming-scheme ps] parts]
    (let [templates (make-templates naming-scheme ps)]
      (= (count templates)
         (expected-number-of-templates ps)))))

