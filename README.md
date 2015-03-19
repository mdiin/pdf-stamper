[![Issues ready for development](https://badge.waffle.io/mdiin/pdf-stamper.png?label=ready&title=Ready)](https://waffle.io/mdiin/pdf-stamper)
[![Issues in progress](https://badge.waffle.io/mdiin/pdf-stamper.png?label=in%20progress&title=In%20progress)](https://waffle.io/mdiin/pdf-stamper)

# pdf-stamper

Combine JSON template description with PDF template files and input data to
form a complete PDF.

## Usage

Not much to see here yet. Library is still under active development.

Feeling adventurous? The library is on Clojars:

[![Clojars Project](http://clojars.org/pdf-stamper/latest-version.svg)](http://clojars.org/pdf-stamper)

## Example: Template format

To give a quick example of how a PDF template description could look, this is an example of a template with a single hole:

```clojure
{:name :template-one-hole
 :holes [{:height 10.0
          :width 10.0
          :x 1.09
          :y 3.12
          :name :lonely-hole
          :type :image
          :priority 10}]}
```

It describes a hole on an actual PDF document page, where data (in this case an image) should be inserted. The data that
makes pdf-stamper use the hole could look like:

```clojure
{:template :template-one-hole
 :locations [{:lonely-hole {:contents {:image (java.io.BufferedImage. "an-image.jpg")}}}]}
```

See [Documentation](#documentation) for further details.

## Documentation

Documentation for both users and developers of pdf-stamper can be found in [the Marginalia docs](https://mdiin.github.io/pdf-stamper).
Users can safely skip the documentation for `pdf-stamper.text.parsed` and `pdf-stamper.text.pdfbox`.

The documentation always describes the latest stable release; to generate the docs for a snapshot release, run `lein marg`.

# Future

First:

- Tests!

After that, in no particular order:

- Hyphenation support
- Text align for parsed text (always left-aligned now)
- Control indentation of first line for parsed text
- Add space after every line of paragraph
- Pluggable text parser, to support other formats than XML (is this even relevant?)

# Acknowledgments

- Ingenium Golf (for letting me work on this)

# License

Copyright © 2014 Matthias Diehn Ingesman

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
