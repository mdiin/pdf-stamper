(ns pdf-stamper.tokenizer.protocols)

;; ## Token functions
;;
(defprotocol Dimensions
  "The dimensions of token when stamped."
  (width [token following-tokens formats context]
         "Stamped width of token, optionally considering a seq of tokens immediately following token.

         following-tokens can be either a function or a seq of tokens.")
  (height [token following-tokens formats context]
          "Stamped height of token, optionally considering a seq of tokens immediately following token.

          following-tokens can be either a function or a seq of tokens."))

(defprotocol Selectable
  "Decision protocol: Is there room for `token` or not?"
  (select [token token-context remaining-space formats context] "Given a token-context vector of [selected tokens, remaining tokens] as well as information on formatting and remaining space, return a (modified) token-context [selected', new-tokens, remaining']; where new-tokens must be nil if there is not enough space left.")
  (horizontal-increase? [token] "True iff tokens results in an increased use of horizontal space."))

(defprotocol Stampable
  "Stamp token to the content stream of a PDF."
  (stamp! [this stream formatting context]))

