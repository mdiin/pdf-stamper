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
  p/Styling
  (styling [this formats]
    (let [paragraph-format (get-in this [:style :format])
          paragraph-properties (get formats paragraph-format)]
      (update
        (select-keys paragraph-properties [:font :style :size :color])
        :style
        (fn [s]
          (disj s (get-in this [:style :character-style]))))))

  p/Dimensions
  (height [this _ _ formats context]
    (let [styling (p/styling this formats)]
      (context/get-font-height
        (:font styling)
        (:style styling)
        (:size styling)
        context)))

  (width [this _ _ formats context]
    (assert (keyword? (:format style)))
    (let [styling (p/styling this formats)]
      (context/get-font-string-width
        (:font styling)
        (:style styling)
        (:size styling)
        word
        context)))

  p/CursorMovement
  (horizontal? [_] true)
  (vertical? [_] false)

  p/Selectable
  (select [token [tokens-selected tokens-remaining] {:keys [remaining-width]} formats context]
    (if (<= (p/width token nil nil formats context) remaining-width)
      [tokens-selected token tokens-remaining]
      [tokens-selected
       ::skip-token
       (into
         [(->NewLine (:style token)) token]
         tokens-remaining)])))

(defrecord Space [style]
  Dimensions
  (height [this _ _ formats context]
    (let [formatting (get formats (:format (:style this)))]
      (context/get-font-height
        (:font formatting)
        (:character-style (:style this))
        (:size formatting)
        context)))

  (width [this _ _ formats context]
    (assert (keyword? (:format style)))
    (let [{:keys [font size style] :as formatting} (get formats (:format style))
          character-style (into style (:character-style style))]
      (context/get-font-string-width font character-style size " " context))))

(defrecord NewLine [style]
  Dimensions
  (height [this xf following-tokens formats context]
    ;(println "Tokens::NewLine::height")
    (let [xform (if (fn? xf)
                  (comp
                    xf
                    (take-while (complement p/horizontal-increase?))
                    (map #(p/height % nil following-tokens formats context)))
                  (comp
                    (take-while (complement p/horizontal-increase?))
                    (map #(p/height % nil following-tokens formats context))))
          formatting (get formats (:format style))]
      (+ (transduce xform max 0 (or following-tokens []))
         (get-in formatting [:spacing :line :above])
         (get-in formatting [:spacing :line :below]))))

  (width [this _ _ formats context]
    0.0))

(defrecord ParagraphBegin [style]
  Dimensions
  (height [this xf following-tokens formats context]
    (println "Tokens::ParapgraphBegin::height")
    (let [xform (if (fn? xf)
                  (comp
                    xf
                    (take-while (complement p/horizontal-increase?))
                    (map #(p/height % nil following-tokens formats context)))
                  (comp
                    (take-while (complement p/horizontal-increase?))
                    (map #(p/height % nil following-tokens formats context))))
          formatting (get formats (:format style))]
      (let [r (+ (transduce xform max 0 (or following-tokens []))
                 (get-in formatting [:spacing :paragraph :above]))]
        (println (str "Height: " r))
        r)))

  (width [this _ _ formats context]
    0.0))

(defrecord ParagraphEnd [style]
  Dimensions
  (height [this _ _ formats context]
    ;(println "Tokens::ParapgraphEnd::height")
    (let [formatting (get formats (:format style))]
      (get-in formatting [:spacing :paragraph :below])))

  (width [this _ _ formats context]
    0.0))

(defrecord NewParagraph [style]
  Dimensions
  (height [this _ _ formats context]
    (paragraph-line-height this formats context))

  (width [this _ _ formats context]
    0.0))

(defrecord NewPage [style]
  Dimensions
  (height [this _ _ formats context]
    ;(println "Tokens::NewPage::height")
    ;; A NewPage token always fills any remaining space on the page.
    Double/POSITIVE_INFINITY)

  (width [this _ _ formats context]
    0.0))

(defrecord ListBullet [style]
  Dimensions
  (height [this _ _ formats context]
    (single-line-height this formats context))

  (width [this _ _ formats context]
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
  (height [this _ _ formats context]
    (single-line-height this formats context))

  (width [this _ _ formats context]
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

;; Internal tokens:

(defrecord LineBegin [style]
  Dimensions
  (height [this xf following-tokens formats context]
    (let [xform (if (fn? xf)
                  (comp
                    xf
                    (take-while (complement p/horizontal-increase?))
                    (map #(p/height % nil following-tokens formats context)))
                  (comp
                    (take-while (complement p/horizontal-increase?))
                    (map #(p/height % nil following-tokens formats context))))
          formatting (get formats (:format style))]
      (+ (transduce xform max 0 (or following-tokens []))
         (get-in formatting [:spacing :line :above]))))

  (width [_ _ _ _ _]
    0.0))

(defrecord LineEnd [style]
  Dimensions
  (height [_ _ _ formats _]
    (let [formatting (get formats (:format style))]
      (get-in formatting [:spacing :line :below]))))

