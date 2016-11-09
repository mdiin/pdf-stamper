(ns pdf-stamper.test-generators
  (:require
    [clojure.test.check :as tc]
    [clojure.test.check.generators :as gen]
    [clojure.test.check.properties :as prop]

    [schema.core :as s]

    [pdf-stamper.tokenizer.tokens :as t]
    [pdf-stamper.schemas :as schema]))

(defn- base-style
  [formats character-styles]
  {:pre [(not-empty formats)
         (not-empty character-styles)]}
  (gen/fmap (partial into {})
            (gen/tuple
              (gen/tuple
                (gen/elements [:format]) (gen/elements formats))
              (gen/tuple
                (gen/return :character-style) (gen/fmap
                                                (partial conj #{})
                                                (gen/elements character-styles))))))

(defn token-word
  [format]
  (gen/fmap (partial apply t/->Word)
            (gen/tuple
              (gen/frequency
                [[9 (base-style [format] [:regular])]
                 [1 (base-style [format] [:bold :italic])]])
              (gen/not-empty gen/string-alphanumeric))))

(defn token-newline
  [format]
  (gen/bind (base-style [format] [:regular])
            (fn [style]
              (gen/return (t/->NewLine style)))))

(defn token-newpage
  [format]
  (gen/bind (base-style [format] [:regular])
            (fn [style]
              (gen/return (t/->NewPage style)))))

(defn token-newparagraph
  [format]
  (gen/bind (base-style [format] [:regular])
            (fn [style]
              (gen/return (t/->NewParagraph style)))))

(defn paragraph-element-token
  [format]
  (gen/frequency
    [[9 (token-word format)]
     [1 (token-newline format)]]))

(defn- bullet
  [format style]
  (condp = format
    :bullet (t/->ListBullet style)
    :number (t/->ListNumber style 1)))

(defn- list-element
  [format]
  (gen/bind (base-style [format] [:regular])
            (fn [style]
              (gen/bind (gen/such-that not-empty
                                       (gen/vector (token-word format)))
                        (fn [words]
                          (gen/return
                            (concat
                              [(bullet format style)]
                              words
                              [(t/->NewLine style)])))))))

(defn- list-elements
  [format]
  (gen/bind (gen/such-that not-empty (gen/vector (list-element format)))
            (fn [list-elements]
              (gen/return
                (flatten list-elements)))))

(defn paragraph
  [format]
  (gen/bind (base-style [format] [:regular])
            (fn [style]
              (gen/bind (condp = format
                          :bullet (list-elements format)
                          :number (list-elements format)
                          (gen/vector (paragraph-element-token format)))
                        (fn [tokens]
                          (gen/return
                            (concat
                              [(t/->ParagraphBegin style)]
                              tokens
                              [(t/->ParagraphEnd style)])))))))

(defn text-element-token
  [format]
  (gen/frequency
    [[7 (paragraph format)]
     [1 (token-newpage format)]
     ]))

(defn text-elements
  [formats]
  (gen/bind (gen/elements formats)
            (fn [format]
              (gen/bind (gen/vector (text-element-token format))
                        (fn [tokens]
                          (gen/return (flatten tokens)))))))

(def spacing
  (gen/fmap (partial into {})
            (gen/tuple
              (gen/tuple
                (gen/return :above) gen/nat)
              (gen/tuple
                (gen/return :below) gen/nat))))

(def indent
  (gen/fmap (partial into {})
            (gen/tuple
              (gen/tuple
                (gen/return :all) gen/nat))))

