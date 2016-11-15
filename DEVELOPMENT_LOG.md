# --- November 15 2016, 21:27 (BRANCH: split-wrapping-and-stamping) ---

I completed the implementation of ListBullet and ListNumber, thereby copmleting
the tokenizer.

It took some time to figure out which parts of the page-maker namespace were still
missing, but I figured it out: The part that processes each hole in the data and
outputs the final vector of pages.

## Next time I open this code base:

Look in the `pdf-stamper.page-maker` namespace.

1. Complete implementation of `contains-parsed-text-holes?`
  - A function to terminate the loop of `data->pages`
  - Templates have potentially different holes on even and odd pages
2. Complete implementation of `process-hole` multimethod
  - Each invocation must return a vector of two elements: The location data for
    any overflow; and the page that results from processing that hole.

## After that

I'm guessing the page making will be the final large step. Upon completing that,
it will be time to hook the page creation algorithm into the rest of the system,
replacing the old implementation.


# --- November 9 2016, 21:13 (BRANCH: split-wrapping-and-stamping) ---

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

