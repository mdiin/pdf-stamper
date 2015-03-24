;; ## Image holes
;;
;; Holes where `:type` is `:image`. In addition to the above keys image holes must have an `:aspect` key.

(ns pdf-stamper.images
  (:import
    [org.apache.pdfbox.pdmodel PDDocument]
    [org.apache.pdfbox.pdmodel.edit PDPageContentStream]
    [org.apache.pdfbox.pdmodel.graphics.xobject PDJpeg PDXObjectImage PDPixelMap]))

(defn- scale-dimensions
  "To calculate the new dimensions for scaled images, the image
  is first scaled such that the height fits into the available
  bounds. If the image width is still larger than it should be,
  it means that the scaling factor for the width is larger than
  for the height, and we use that to compute a new height.

  The arguments are passed as a map to provide some context to
  the four numbers, as it is otherwise too easy to mix up the
  parameters when applying this function.
  
  *Future*: Going by the description above it should be possible
  to refactor this to compute both scaling factors up front, and
  simply use the largest."
  [{:keys [b-width b-height i-width i-height]}]
  (let [height-factor (/ b-height i-height)
        new-width (* i-width height-factor)]
    (if (> new-width b-width)
      (let [width-factor (/ b-width new-width)
            new-height (* b-height width-factor)]
        [b-width new-height])
      [new-width b-height])))

(defn- draw-image-preserve-aspect
  "Stamping an image onto the PDF's content stream while still
  preserving aspect ratio potentially requires moving the image's
  origin. `new-x` and `new-y` move the image origin by half the
  scaled images delta width and height."
  [c-stream image data]
  (let [{:keys [x y width height]} data
        awt-image (.. image (getRGBImage))
        img-height (.. awt-image (getHeight))
        img-width (.. awt-image (getWidth))
        [scaled-width scaled-height] (scale-dimensions {:b-width width
                                                        :b-height height
                                                        :i-width img-width
                                                        :i-height img-height})
        new-x (+ x (Math/abs (/ (- width scaled-width) 2)))
        new-y (+ y (Math/abs (/ (- height scaled-height) 2)))]
    (.. c-stream (drawXObject image new-x new-y scaled-width scaled-height))))

(defn- draw-image
  "Stamping an image onto the PDF's content stream without
  preserving aspect ratio is much simpler: PDFBox resizes the
  image to fill the entire box specified by `width` and `height`,
  potentially skewing the image."
  [c-stream image data]
  (let [{:keys [x y width height]} data]
    (.. c-stream (drawXObject image x y width height))))

(defn fill-image
  "When stamping an image, the image is always shrunk to fit the
  dimensions of the hole. The value of the `:aspect` key in `data`
  defines whether the image is shrunk to fit, or aspect ratio is
  preserved. Possible values are `:fit` or `:preserve`, with
  `:preserve` being the default.

  It is possible to specify the quality of the stamped image by
  setting the `:quality` key to a value between `0.0` and `1.0`. The
  default quality if not specified is `0.75`.
  
  *Note*: Using `PDJpeg` does not cancel out support for PNGs. It
  seems that the PNGs are internally converted to JPEGs (**TO BE
  CONFIRMED**)."
  [document c-stream data context]
  (let [aspect-ratio (get data :aspect :preserve)
        image-quality (get data :quality 0.75)
        image (PDJpeg. document (get-in data [:contents :image]) image-quality)]
    (assert image "Image must be present in hole contents.")
    (condp = aspect-ratio
      :preserve (draw-image-preserve-aspect c-stream image data)
      :fit (draw-image c-stream image data))))

