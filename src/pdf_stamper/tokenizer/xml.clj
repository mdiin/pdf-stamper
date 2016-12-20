(ns pdf-stamper.tokenizer.xml
  (:require
    [clojure.data.xml :as xml]

    [pdf-stamper.tokenizer :refer [Tokenizable tokenize]]
    [pdf-stamper.tokenizer.standard :as standard-tokenizers]
    [pdf-stamper.tokenizer.tokens :as token]))

(defn- t-paragraph-elm
  [elm-type]
  (fn [content style]
    (let [new-style (assoc style :format elm-type)]
      (-> []
          (conj (token/->ParagraphBegin new-style))
          (into (flatten (tokenize content new-style)))
          (conj (token/->ParagraphEnd new-style)))))

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
                        (update-in [:list :indent :level] (fnil inc 0)))]
      [(token/->ParagraphBegin new-style)
       (tokenize content new-style)
       (token/->ParagraphEnd new-style)])))

(def t-unordered-list (t-list-elm :bullet))
(def t-ordered-list (t-list-elm :number))

;; (t-bullet | t-number) -> [ts] -> t-new-line
(defn- t-list-item
  [content style]
  (let [_ (update-in style [:list :numbering] swap! inc)]
    [(if (= (get-in style [:list :type]) :bullet)
       (token/->ListBullet style)
       (token/->ListNumber style @(get-in style [:list :numbering])))
     (tokenize content style)
     (token/->NewLine style)]))

;; [ts]
(defn- t-em
  [content style]
  (let [new-style (update-in style [:character-style] (fnil conj #{}) :italic)]
    [(tokenize content new-style)]))

;; [ts]
(defn- t-strong
  [content style]
  (let [new-style (update-in style [:character-style] (fnil conj #{}) :bold)]
    [(tokenize content new-style)]))

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
     :br [(token/->NewLine style)]
     :em (t-em (:content elm-or-str) style)
     :i (t-em (:content elm-or-str) style)
     :strong (t-strong (:content elm-or-str) style)
     :b (t-strong (:content elm-or-str) style)
     (tokenize (:content elm-or-str) style))))

(extend-type clojure.data.xml.Element
  Tokenizable

  (tokenize
    ([elm] (tokenize elm nil))
    ([elm style] (tokenize-xml* elm style))))

