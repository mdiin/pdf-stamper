(ns pdt.templates
  (:require
    [clojure.tools.reader.reader-types :as reader]
    [clojure.edn :as edn])
  (:import
    [org.apache.pdfbox.pdmodel PDDocument]
    [org.apache.pdfbox.pdmodel.edit PDPageContentStream]
    [org.apache.pdfbox.pdmodel.graphics.xobject PDXObjectImage PDPixelMap]))

(def ^:private templates (atom {}))

(defn add-template
  [in-stream]
  (let [push-back-reader (reader/input-stream-push-back-reader in-stream)
        template (edn/read {:eof nil} push-back-reader)]
    (when template
      (swap! templates assoc (:name template) template))))

(defn find-template
  [name]
  (get @templates name))

