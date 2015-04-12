(ns pdf-stamper.tokenizer.tokens
  (:require
    [pdf-stamper.protocols :as p :refer [Token]]
    [pdf-stamper.context :as context]))

;; ## Tokens
;; A token is the combination of a single word plus any styling of that
;; word, or one of the special tokens.

(defn- single-line-height
  [token formats context]
  (let [formatting (get formats (:format (:style token)))]
    (+ (context/get-font-height
         (:font formatting)
         (:character-style (:style token))
         (:size formatting)
         context)
       (get-in formatting [:spacing :line :above])
       (get-in formatting [:spacing :line :below]))))

(defrecord Word [style word]
  Token
  (height [this formats context]
    (single-line-height this formats context))
  
  (width [this formats context]
    (let [formatting (get formats (:format style))]
      (context/get-font-string-width
        (:font formatting)
        (:character-style style)
        (:size formatting)
        word
        context))))

(defrecord NewLine [style]
  Token
  (height [this formats context]
    (single-line-height this formats context))
  
  (width [this formats context]
    0.0))

(defrecord NewParagraph [style])
(defrecord NewPage [style])
(defrecord ListBullet [style])
(defrecord ListNumber [style number])

