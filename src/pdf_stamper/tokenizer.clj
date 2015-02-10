(ns pdf-stamper.tokenizer
  (:require
    [clojure.data.xml :as xml]))

(defprotocol Tokenizable
  "Implementations of this protocol can be converted to tokens
  in the style required by the the data format internal to pdf-stamper.
  `tokenize` will always return a seq of tokens."
  (tokenize [this] [this style]))

