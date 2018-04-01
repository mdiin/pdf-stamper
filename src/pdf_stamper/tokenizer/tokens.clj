(ns pdf-stamper.tokenizer.tokens
  (:require
    [pdf-stamper.tokenizer.protocols :as p :refer [Dimensions]]
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
  Dimensions
  (height [this _ formats context]
    ;(println "Tokens::Word::height")
    (let [formatting (get formats (:format (:style this)))]
      (context/get-font-height
        (:font formatting)
        (:character-style (:style this))
        (:size formatting)
        context)))

  (width [this _ formats context]
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

(defrecord Space [style]
  Dimensions
  (height [this _ formats context]
    (context/get-font-height
      (:font formatting)
      (:character-style (:style this))
      (:size formatting)
      context))

  (width [this _ formats context]
    (assert (keyword? (:format style)))
    (let [{:keys [font size style] :as formatting} (get formats (:format style))
          character-style (into style (:character-style style))]
      (context/get-font-string-width font character-style size " " context))))

(defrecord NewLine [style]
  Dimensions
  (height [this following-tokens formats context]
    ;(println "Tokens::NewLine::height")
    (apply + (into []
                   (comp
                     (take-while (complement p/horizontal-increase))
                     (map #(p/height % following-tokens formats context)))
                   followin-tokens)))

  (width [this _ formats context]
    0.0))

(defrecord ParagraphBegin [style]
  Dimensions
  (height [this _ formats context]
    ;(println "Tokens::ParapgraphBegin::height")
    (let [formatting (get formats (:format style))]
      ;(println formatting)
      (+ (single-line-height this formats context)
         (get-in formatting [:spacing :paragraph :above]))))

  (width [this _ formats context]
    0.0))

(defrecord ParagraphEnd [style]
  Dimensions
  (height [this _ formats context]
    ;(println "Tokens::ParapgraphEnd::height")
    (let [formatting (get formats (:format style))]
      (get-in formatting [:spacing :paragraph :below])))

  (width [this _ formats context]
    0.0))

(defrecord NewParagraph [style]
  Dimensions
  (height [this _ formats context]
    (paragraph-line-height this formats context))

  (width [this _ formats context]
    0.0))

(defrecord NewPage [style]
  Dimensions
  (height [this _ formats context]
    ;(println "Tokens::NewPage::height")
    ;; A NewPage token always fills any remaining space on the page.
    Double/POSITIVE_INFINITY)

  (width [this _ formats context]
    0.0))

(defrecord ListBullet [style]
  Dimensions
  (height [this _ formats context]
    (single-line-height this formats context))

  (width [this _ formats context]
    (assert (keyword? (:format style)))
    (let [formatting (get formats (:format style))
          bullet-char (get formatting :bullet-char " ")
          bullet-width (context/get-font-string-width
                         (:font formatting)
                         #{:regular}
                         (:size formatting)
                         bullet-char
                         context)
          text-spacing (get formatting :text-spacing (:size formatting))]
      (+ bullet-width text-spacing))))

(defrecord ListNumber [style number]
  Dimensions
  (height [this _ formats context]
    (single-line-height this formats context))

  (width [this _ formats context]
    (assert (keyword? (:format style)))
    (let [formatting (get formats (:format style))
          number-width (context/get-font-string-width
                         (:font formatting)
                         #{:regular}
                         (:size formatting)
                         (str number)
                         context)
          text-spacing (get formatting :text-spacing (:size formatting))]
      (+ number-width text-spacing))))

