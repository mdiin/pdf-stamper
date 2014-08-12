(ns pdf-stamper.pdf.images
  (:import
    [org.apache.pdfbox.pdmodel PDDocument]
    [org.apache.pdfbox.pdmodel.edit PDPageContentStream]
    [org.apache.pdfbox.pdmodel.graphics.xobject PDXObjectImage PDPixelMap]))

(defn- scale-dimensions
  [{:keys [b-width b-height i-width i-height]}]
  (let [height-factor (/ b-height i-height)
        new-width (* i-width height-factor)]
    (if (> new-width b-width)
      (let [width-factor (/ b-width new-width)
            new-height (* b-height width-factor)]
        [b-width new-height])
      [new-width b-height])))

(defn- draw-image-preserve-aspect
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
  [c-stream image data]
  (let [{:keys [x y width height]} data]
    (.. c-stream (drawXObject image x y width height))))

(defn fill-image
  "c-stream: a PDPageContentStream object
  data: a map combining the area descriptions with the data
  context: fonts, templates, etc.

  Example of area:
  {:height ..
   :width ..
   :x ..
   :y ..
   :name ..
   :type :image
   :contents {:image java.awt.BufferedImage}}"
  [document c-stream data context]
  (let [aspect-ratio (get data :aspect :preserve)
        image (PDPixelMap. document (get-in data [:contents :image]))]
    (condp = aspect-ratio
      :preserve (draw-image-preserve-aspect c-stream image data)
      :fit (draw-image c-stream image data))))

