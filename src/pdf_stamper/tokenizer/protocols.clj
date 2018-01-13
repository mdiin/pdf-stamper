(ns pdf-stamper.tokenizer.protocols)

;; ## Token functions
;;
(defprotocol Dimensions
  "Deriving information from tokens."
  (width [token formats context])
  (height [token formats context]))

(defprotocol Selectable
  "Decision protocol: Is there room for `token` or not?"
  (select [token remaining-space formats context])
  (horizontal-increase? [token]))

(defprotocol Stampable
  "Stamp token to the content stream of a PDF."
  (stamp! [this stream formatting context]))

