(ns pdf-stamper.tokenizer.protocols)

;; ## Token functions
;;
(defprotocol Dimensions
  "The dimensions of token when stamped."
  (width [token xf following-tokens formats context]
         "Stamped width of token, optionally considering a seq of tokens immediately following token.

  xf is a transducing function applied to filter the seq of following-tokens.")
  (height [token xf following-tokens formats context]
          "Stamped height of token, optionally considering a seq of tokens immediately following token.

  xf is a transducing function applied to filter the seq of following-tokens."))

(defprotocol Styling
  (styling [token formats] "The styling of token."))

(defprotocol CursorMovement
  (horizontal? [token] "Does token cause PDF cursor to move horizontally?")
  (vertical? [token] "Does token cause PDF cursor to move vertically?"))

(defprotocol Selectable
  "Decision protocol: Is there room for `token` or not?"
  (select [token token-context remaining-space formats context] "Given a token-context vector of [selected tokens, remaining tokens] as well as information on formatting and remaining space, return a (modified) token-context [selected', new-tokens, remaining']; where new-tokens must be nil if there is not enough space left.")
  (horizontal-increase? [token] "True iff tokens results in an increased use of horizontal space."))

(defprotocol Stampable
  "Stamp token to the content stream of a PDF."
  (stamp! [this stream formatting context]))

