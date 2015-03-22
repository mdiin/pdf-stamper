(ns pdf-stamper.tokenizer.tokens)

;; ## Tokens
;; A token is the combination of a single word plus any styling of that
;; word, or one of the special tokens.
(defrecord Token [kind style word])

(defn t-word
  "Word token"
  [style word]
  (->Token ::word style word))

;; ### Special tokens
(defn t-new-paragraph
  "New paragrah token."
  [style]
  (->Token ::new-paragraph style nil))

(defn t-new-line 
  "New line token."
  [style]
  (->Token ::new-line style nil))

(defn t-new-page
  "New page token."
  [style]
  (->Token ::new-page style nil))

(defn t-bullet
  "Bullet token."
  [style]
  (->Token ::bullet style nil))

(defn t-number
  "Number token."
  [style n]
  (->Token ::number style n))

