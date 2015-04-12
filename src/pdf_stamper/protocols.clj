(ns pdf-stamper.protocols)

;; ## Token functions
;;
(defprotocol Token
  "Deriving information from tokens."
  (width [token formats context])
  (height [token formats context]))

(defprotocol SelectToken
  "Decision protocol: Is there room for `token` or not?"
  (select-token [token remaining-space formats context])
  (horizontal-increase? [token]))

