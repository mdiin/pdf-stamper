;; User input to pdf-stamper is validated using the schema library from Prismatic.

(ns pdf-stamper.schemas
  (:require
    [schema.core :as s]))

(def BaseHole
  {:height s/Num
   :width s/Num
   :x s/Num
   :y s/Num
   :name s/Keyword
   :priority s/Int})

(def ImageHole
  (merge BaseHole
         {:type (s/enum :image)
          (s/optional-key :quality) s/Num
          (s/optional-key :aspect) (s/enum :preserve :fit)}))

(def ParagraphFormat
  {:font s/Keyword
   :style #{s/Keyword}
   :size s/Int
   :color [(s/one s/Int "R") (s/one s/Int "G") (s/one s/Int "B")]
   :spacing {:paragraph {:above s/Num
                         :below s/Num}
             :line {:above s/Num
                    :below s/Num}}
   :indent {:all s/Num}})

(def BulletParagraphFormat
  (merge ParagraphFormat
         {(s/optional-key :bullet-char) s/Str}))

(def TextHole
  (merge BaseHole
          {:format ParagraphFormat
           :type (s/enum :text)
           :align {:horizontal (s/enum :center :left :right)
                   :vertical (s/enum :center :top :bottom)}}))

(def TextParsedHole
  (merge BaseHole
         {:type (s/enum :text-parsed)
          :format {:paragraph ParagraphFormat
                   :head-1 ParagraphFormat
                   :head-2 ParagraphFormat
                   :head-3 ParagraphFormat
                   :bullet BulletParagraphFormat
                   :number BulletParagraphFormat}}))

(def Hole
  (s/conditional
    #(= :image (:type %)) ImageHole
    #(= :text (:type %)) TextHole
    #(= :text-parsed (:type %)) TextParsedHole
    'has-valid-type-key))

(def hole-checker (s/checker Hole))
(defn valid-hole?
  "Return v if v is a valid hole, false otherwise.
  
  If validation-err-fn is supplied, calls that function with the error message.
  The return value of validation-error-fn is discarded."
  ([v]
   (not (hole-checker v)))

  ([v error-fn]
   {:pre [(fn? error-fn)]}
   (if-let [err (hole-checker v)]
     (do
       (error-fn (merge v (if (map? err)
                            err
                            {:error err})))
       false)
     true)))

(def Template
  {:name s/Keyword
   (s/optional-key :overflow) s/Keyword
   :holes [Hole]})

(defn validation-errors
  [template]
  (s/check Template template))

