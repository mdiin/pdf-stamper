(ns pdf-stamper.tokenizer
  (:require
    [clojure.data.xml :as xml]))

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

(defprotocol Tokenizable
  "Implementations of this protocol can be converted to tokens
  in the style required by the the data format internal to pdf-stamper.
  `tokenize` will always return a seq of tokens."
  (tokenize [this] [this style]))

;; ## Standard tokenizers
;;
;; These are important for base cases to the more involved tokenizers.

(defn- tokenize-str*
  [s style]
  (map #(->Token ::word style %) (clojure.string/split s #" ")))

(extend-type java.lang.String
  Tokenizable
  
  (tokenize 
    ([s] (tokenize s nil))
    ([s style] (tokenize-str* s style))))

(defn- tokenize-seq*
  [s style]
  (map #(tokenize % style) s))

(extend-type clojure.lang.LazySeq
  Tokenizable
  
  (tokenize
    ([s] (tokenize s nil))
    ([s style] (tokenize-seq* s style))))

;; ## XML tokenizer

(defn- t-paragraph-elm
  [elm-type]
  (fn [content style]
    (let [new-style (assoc style :format elm-type)]
      [(tokenize content new-style)
       (t-new-paragraph new-style)])))

(def t-paragraph (t-paragraph-elm :paragraph))
(def t-head-1 (t-paragraph-elm :head-1))
(def t-head-2 (t-paragraph-elm :head-2))
(def t-head-3 (t-paragraph-elm :head-3))

(defn- t-list-elm
  [elm-type]
  (fn [content style]
    (let [new-style (-> style
                        (assoc :format elm-type)
                        (assoc-in [:list :type] elm-type)
                        (assoc-in [:list :numbering] (atom 0))
                        (update-in [:indent :level] (fnil inc 0)))]
      [(tokenize content new-style)
       (t-new-paragraph new-style)])))

(def t-unordered-list (t-list-elm :bullet))
(def t-ordered-list (t-list-elm :number))

(defn- t-list-item
  [content style]
  (let [_ (update-in style [:list :numbering] swap! inc)]
    [(if (= (get-in style [:list :type]) :bullet)
       (t-bullet style)
       (t-number style @(get-in style [:list :numbering])))
     (tokenize content style)
     (t-new-line style)]))

(defn- tokenize-xml*
  "Tokenize elm and use style as the base for it's tokens' styles."
  [elm-or-str style]
  (if (string? elm-or-str)
    (tokenize elm-or-str style)
    (condp = (:tag elm-or-str)
     :p (t-paragraph (:content elm-or-str) style)
     :h1 (t-head-1 (:content elm-or-str) style)
     :h2 (t-head-2 (:content elm-or-str) style)
     :h3 (t-head-3 (:content elm-or-str) style)
     :ul (t-unordered-list (:content elm-or-str) style)
     :ol (t-ordered-list (:content elm-or-str) style)
     :li (t-list-item (:content elm-or-str) style)
     :br [(t-new-line style)]
     (tokenize (:content elm-or-str) style))))

(extend-type clojure.data.xml.Element
  Tokenizable

  (tokenize
    ([elm] (tokenize elm nil))
    ([elm style] (tokenize-xml* elm style))))

