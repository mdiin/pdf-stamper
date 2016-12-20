# --- December 20 2016, 20:44 (BRANCH: split-wrapping-and-stamping) ---

Implemented the remaining two parts of the `page-maker/process-hole` multimethod.
While running through some trivial and made-up data I noticed that the XML
tokenizer did not return a vector of unnested tokens, so I fixed that.

It is quite difficult to reason about whether or not the code does as is intended,
simply because it can be hard to remember what form the various arguments can take.
Consider using core.spec, since it will be baked into Clojure from version 1.9.

Before I run along any further I should probably investigate core.spec. I have a
feeling that just writing the specs for most of the functions will find some bugs.

## Next time I open this code base:

One of two possible roads open up here:

1. Start spec'ing functions in the `page-maker` namespace
2. Start implementing tests and test data for the functions in the `page-maker`
   namespace

## After that

Really depends on which course of action was decided above.


# --- December 2 2016, 22:04 (BRANCH: split-wrapping-and-stamping) ---

Major insight of the day: I do not have to limit holes to the same dimensions
across even and odd pages! Explanation follows:

It is possible to process data for a PDF linearly, i.e. such that we only start
building pages for the next piece of data once we have completely built the data
for any previous pieces of data. That way we can know whether we should be generating
the page using the template for even or odd.

The above can become problematic if I need to parallelize page creation even
further. Should that become necessary: Look at letting `page-maker/data->pages`
return a pair of page-sets; one for when the first page is odd, and one for even.
They could even be wrapped in `delay`s, to make sure not to eat too much CPU with
unnecessary computation.

The current implementation of `page-maker/data->pages` could potentially use `pmap`
for the calls to `page-maker/process-hole`. Will need to benchmark before making
a decision.

## Next time I open this code base:

Implement `page-maker/process-hole` for image and plain/non-parsed text holes.

## After that

1. Start building some test data for the `page-maker/data->pages` function to see
   if it really works as expected.


# --- November 30 2016, 20:18 (BRANCH: split-wrapping-and-stamping) ---

This time I was interrupted by the phone, so I only got 20 minutes of coding today.

I completed the implementation of getting dimensions of a template hole.

## Next time I open this code base:

Same as last time... Hopefully this will be the last time I have to write this.

## After that

Again, same as last time.


# --- November 22 2016, 21:00 (BRANCH: split-wrapping-and-stamping) ---

The first of the two remaining steps from last time got completed today.

I noticed that templates specify holes based on even and odd pages, so that the
`pdf-stamper.page-maker/process-hole` multimethod needs to take that into account.
There was a major problem with that: How do we do parallel processing in the face
of changing hole dimensions for the same holes across even/odd pages? The simple
answer is: We don't. So I added a constraint on the template schema, such that
the same `:parsed-text` hole must have the same dimensions on both even and odd
pages. It is still possible to have `:parsed-text` holes on one set of pages that
are not present on the other.

After that insight, not much more was done today.

## Next time I open this code base:

Look in the `pdf-stamper.page-maker` namespace.

1. Complete implementation of `process-hole` multimethod
  - Each invocation must return a vector of two elements: The location data for
    any overflow; and the page that results from processing that hole.

## After that

Profit! Seriously though, see notes from November 15 2016.


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

