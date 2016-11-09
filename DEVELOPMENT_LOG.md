# November 9 2016, 21:13 (BRANCH: split-wrapping-and-stamping)

I started getting back into the code base, specifically the work in progress that
is the tokenization algorithm.

By reading the code I noticed that the namespace `pdf-stamper.pages.text-wrap` is
basically a copy of the algorithm in `pdf-stamper.text.parsed`. It looks like it
whould be removed as part of the work ongoing in this branch.

My first step into changing anything was making sure the generative tests were
stille running. After that I began implementing the `Token` protocol on bullet
tokens. Here I hit a bump, as running the tests produces a `NullPointerException`.
The minimal case producing the error is useful:

ParagraphBegin (bullet) -> ListBullet -> Word -> NewLine -> ParagraphEnd (bullet)

## Next time I open this code base:

1. Build a minimal failing example
2. Insert traces all around the code to track down where the NPE occurs
3. Run `pdf-stamper.page-maker/split-tokens` on the minimal failing example from (1)

## After that

The immediate next step after tracking down the NPE is to implement the `Token`
protocol for `ListNumber`.

Once that is done, the tokenizer is complete (for now). This probably means trying
to use the tokenizer instead of the line wrapping algorithm implemented in
 `pdf-stamper.text.parsed`.

