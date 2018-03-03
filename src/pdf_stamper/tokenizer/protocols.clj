(ns pdf-stamper.tokenizer.protocols)

;; ## Token functions
;;
(defprotocol Dimensions
  "Deriving information from tokens."
  (width [token formats context])
  (height [token formats context]))

(defprotocol Selectable
  "Decision protocol: Is there room for `token` or not?"
  (select [token token-context remaining-space formats context] "Given a token-context vector of [selected tokens, remaining tokens] as well as information on formatting and remaining space, return a (modified) token-context [selected', new-tokens, remaining']; where new-tokens must be nil if there is not enough space left.")
  (horizontal-increase? [token] "True iff tokens results in an increased use of horizontal space."))

(defprotocol Stampable
  "Stamp token to the content stream of a PDF."
  (stamp! [this stream formatting context]))

