# pdt

Combine JSON template description with PDF template files and input data to
form a complete PDF.

## Usage

Not much to see here yet. Library is still under active development.

## Data format

### Template specifications

Each template specification is for a one page document, describing "holes" on a PDF page. It contains these fields:

- `:name` - the name of the template
- `:overflow` - the template to use when a `:text` hole on this template overflows
- `:holes` - the holes available in the template

`:overflow` is optional, and if not present simply means that texts will get truncated. Holes contain a number of fields:

- `:height` - in PDF points
- `:width` - in PDF points
- `:x` - in PDF points
- `:y` - in PDF points
- `:name` - the name of this hole on the page, e.g. `:head/hole-number`
- `:type` - `:image`, `:text`, or `:parsed-text`
- `:format` - a map of maps containing the keys `:font`, `:style`, `:size`, `:color`, `:spacing`, `:indent`, `:spacing`
  - `:paragraph`
  - `:bullet`
  - `:number`
  - `:head-1`
  - `:head-2`
  - `:head-3`
- `:align` - a map with the keys `:horizontal` and `:vertical`
- `:priority` - when in the drawing process to write this hole to the PDF

`:x, :y` defines the top left corner of the hole on the PDF template page. `:height` and `:width` should be self-explanatory.

`:priority` is effectively a layering of the contents on template pages; e.g. if you have two overlapping holes on a template the one with the lowest value in `:priority` will be drawn on the page first, and the other hole on top of that.

`:format` is only necessary for `:text` (only the `:paragraph` key is necessary) and `:parsed-text` (all keys are necessary) holes. The keys `:font` and `:size` must be in points (`pt`), not pixels (`px`). `:color` must be an RGB vector, i.e. `[red green blue]`. `:spacing` is a map with the keys `:paragraph` and `:line`, each with the keys `:above` and `:below`. `:indent` is a map with the keys `:all`, each of which is in points.

`:bullet` paragraphs must contain the key `:bullet-char`, a string to use as the bullet in bullet lists.

`:align :horizontal` respects the values `:left`, `:right`, and `:center`; `:align :vertical` respects `:top`, `:bottom`, and `:center`. It is only used on `:text` holes, i.e. images and parsed text is not aligned according to these values.

The template format is [EDN](https://github.com/edn-format/edn).

Example of a single hole template:

```clojure
{:name :single-hole-template
 :holes [{:height 10.0
          :width 10.0
          :x 1.09
          :y 3.12
          :name :head/hole-number
          :type :image
          :priority 10}]}
```

When adding a template to use there are two options: Adding it with a local file name, or adding it with a `java.io.URL` instance.

### Program input

Input to the program consists of the fields:

- `:pages` - an ordered list of data for the pages

Each entry in the `:pages` list is a map with the following keys:

- `:template` - which page template to use, e.g. `:front-page`
- `:locations` - what to put on this page, a map

Keys in the `:locations` map match `:name` keys in the template hole specifications; the values' contents depend on the `:type` of the hole in the template. For `:image`:

- `:image` - a `java.awt.BufferedImage`

For `:text` and `:parsed-text`:

- `:text` - the actual text to insert

In `:text` holes the text is inserted as is, while in `:text-parsed` it is parsed as XML/HTML. The string must consist of a top-level element containing the entire text. Only the following tags are handled with formatting:

- Paragraph level tags:
  - `h1-3` as headings
  - `p` as paragraphs
  - `ul` as bullet lists
  - `ol` as numbered lists
- Character level tags:
  - `em` as emphasized text (i.e. italics)
  - `i` same as `em`
  - `strong` as bold text
  - `b` same as `strong`

Paragraph level tags cannot be nested.

If a word is longer than the by the template specified width, the line will overflow the specified box.

Input to the program is, like the template specifications, in [EDN](https://github.com/edn-format/edn).

Example (a one page document):

```clojure
{:style :regular
 :pages [{:kind :hole_2_sponsors
          :locations {:head/hole-number {:text "<t>1</t>"}}]}
```

## TODO: Fonts

The library supports the standard fonts of [PDFBox](https://pdfbox.apache.org/). To include custom fonts, use ...

In the templates fonts are specified by a combination of their names (as a keyword) and a style (a set of keywords). If a font in a template is not found a default font is used (currently Times New Roman) with the chosen style; if the style is not found either it will default to the regular font.

# Future

In no particular order:

- Hyphenation support
- Text align for parsed text (always left-aligned now)
- Control indentation of first line for parsed text
- Pluggable text parser, to support other formats than XML

# Acknowledgments

- Ingenium Golf (for letting me work on this)

# License

Copyright © 2014 Matthias Diehn Ingesman

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
