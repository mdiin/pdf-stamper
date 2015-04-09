(ns pdf-stamper.protocols)

;; ## Token functions
;;
;; Deriving information from tokens, such as a tokens width and height.
(defprotocol Token
  (width [token formats context])
  (height [token formats context]))

(defprotocol SelectToken
  (select-token [token remaining-space formats context]))

