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
    ;(println (str "Tokens::single-line-height -> ENTER"))
    (let [res (+ (context/get-font-height
                   (:font formatting)
                   (:character-style (:style token))
                   (:size formatting)
                   context)
                 (get-in formatting [:spacing :line :above])
                 (get-in formatting [:spacing :line :below]))]
      ;(println (str "Tokens::single-line-height -> LEAVE (" res ")"))
      res
      )))

(defn- paragraph-line-height
  [token formats context]
  (let [formatting (get formats (:format (:style token)))]
    (+ (single-line-height token formats context)
       (get-in formatting [:spacing :paragraph :above])
       (get-in formatting [:spacing :paragraph :below]))))

(defrecord Word [style word]
  Token
  (height [this formats context]
    ;(println "Tokens::Word::height")
    (single-line-height this formats context))
  
  (width [this formats context]
    ;(println (str "tokens::Word::width -> ENTER"))
    (assert (keyword? (:format style)))
    (let [formatting (get formats (:format style))]
      (let [res (context/get-font-string-width
              (:font formatting)
              (:character-style style)
              (:size formatting)
              word
              context)]
        ;(println (str "tokens::Word::width -> LEAVE (" res ")"))
        res))))

(defrecord NewLine [style]
  Token
  (height [this formats context]
    ;(println "Tokens::NewLine::height")
    (single-line-height this formats context))
  
  (width [this formats context]
    0.0))

(defrecord ParagraphBegin [style]
  Token
  (height [this formats context]
    ;(println "Tokens::ParapgraphBegin::height")
    (let [formatting (get formats (:format style))]
      ;(println formatting)
      (+ (single-line-height this formats context)
         (get-in formatting [:spacing :paragraph :above]))))
  
  (width [this formats context]
    0.0))

(defrecord ParagraphEnd [style]
  Token
  (height [this formats context]
    ;(println "Tokens::ParapgraphEnd::height")
    (let [formatting (get formats (:format style))]
      (+ (single-line-height this formats context)
         (get-in formatting [:spacing :paragraph :below]))))
  
  (width [this formats context]
    0.0))

(defrecord NewParagraph [style]
  Token
  (height [this formats context]
    (paragraph-line-height this formats context))
  
  (width [this formats context]
    0.0))

(defrecord NewPage [style]
  Token
  (height [this formats context]
    ;(println "Tokens::NewPage::height")
    ;; A NewPage token always fills any remaining space on the page.
    Double/POSITIVE_INFINITY)
  
  (width [this formats context]
    0.0))

(defrecord ListBullet [style]
  Token
  (height [this formats context]
    0.0)
  
  (width [this formats context]
    0.0))

(defrecord ListNumber [style number])

