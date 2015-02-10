(ns pdf-stamper.tokenizer.tokens)

;; ## Tokens
;; A token is the combination of a single word plus any styling of that
;; word, or one of the special tokens.
(defrecord Token [kind style word])

;; ### Special tokens
(defn t-new-paragraph
  "New paragrah token."
  [style]
  (->Token ::paragraph style nil))

(defn t-new-line 
  "New line token."
  [style]
  (->Token ::line style nil))

(defn t-new-page
  "New page token."
  [style]
  (->Token ::page style nil))

(defn t-bullet
  "Bullet token."
  [style]
  (->Token ::bullet style nil))

(defn t-number
  "Number token."
  [style n]
  (->Token ::number style n))

