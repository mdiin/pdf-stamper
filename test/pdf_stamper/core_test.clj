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

