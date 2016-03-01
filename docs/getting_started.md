# Getting started

Start by `require`ing `pdf-stamper`'s main namespace.

```clojure
(ns stamper.example
  (:require
    [pdf-stamper :as stamper]))
```

Have a PDF file handy, for use as the basis of your template.

Create a template description:

```clojure
(def template-description {:name :my-template
                           :holes [{:name :hole-1
                                    :x 0.0
                                    :y 0.0
                                    :width 10.0
                                    :height 20.0
                                    :type :text
                                    :format {:font :times
                                             :style #{:regular}
                                             :size 16
                                             :color [100 100 100]
                                             :spacing {:line {:above 0
                                                              :below 0}
                                                       :paragraph {:above 0
                                                                   :below 0}}
                                             :indent {:all 0}}
                                    :align {:horizontal :center
                                            :vertical :center}
                                    :priority 1}]})
```

For the details on template descriptions, see ???.

Add a template to the context, starting with the base context:

```clojure
(def pdf-uri (clojure.io/resource "path/to/base.pdf"))

(def context (->> stamper/base-context
                  (stamper/add-template template-description pdf-uri))
```

Now you are ready to stamp that 10 by 20 points hole in your PDF with whatever data you see fit:

```clojure
(def pages [{:template :my-template
             :locations {:hole-1 {:contents {:text "This is page 1"}}}}
             
            {:template :my-template
             :locations {:hole-1 {:contents {:text "This is page 2"}}}}

            {:template :my-template
             :locations {:hole-1 {:contents {:text "This is page 3"}}}}])

(def out (stamper/fill-pages pages context))
```

`out` is a `java.io.ByteArrayOutputStream`, which is easily written to a file like so:

```clojure
(.writeTo out (java.io.FileOutputStream. "my-new-doc.pdf"))
(.close out)
```

That's it! You have successfully created a template description and written a three page document using that template description.

