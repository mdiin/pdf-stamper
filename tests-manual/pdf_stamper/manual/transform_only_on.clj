(ns pdf-stamper.manual.transform-only-on
  (:require
    [pdf-stamper :refer :all]
    [clojure.edn :as edn]))

(def t1-fill {:name :t1-fill
              :holes [{:name :c
                       :height 50.0
                       :width 80.0
                       :x 10.0
                       :y 10.0
                       :type :text
                       :align {:horizontal :left
                               :vertical :center}
                       :format {:font :times
                                :style #{:regular}
                                :size 12
                                :color [0 0 0]
                                :indent {:all 0}
                                :spacing {:paragraph {:below 0
                                                      :above 0}
                                          :line {:below 0
                                                 :above 0}}}
                       :priority 2}]})

(def t1 {:name :t1
         :only-on {:pages :even
                   :filler :t1-fill}
         :holes [{:name :c
                  :height 50.0
                  :width 80.0
                  :x 10.0
                  :y 10.0
                  :type :text
                  :align {:horizontal :left
                          :vertical :center}
                  :format {:font :times
                           :style #{:regular}
                           :size 12
                           :color [0 0 0]
                           :indent {:all 0}
                           :spacing {:paragraph {:below 0
                                                 :above 0}
                                     :line {:below 0
                                            :above 0}}}
                  :priority 2}]})

(def t2-fill {:name :t2-fill
              :holes [{:name :c
                       :height 50.0
                       :width 80.0
                       :x 10.0
                       :y 10.0
                       :type :text
                       :align {:horizontal :left
                               :vertical :center}
                       :format {:font :times
                                :style #{:regular}
                                :size 12
                                :color [0 0 0]
                                :indent {:all 0}
                                :spacing {:paragraph {:below 0
                                                      :above 0}
                                          :line {:below 0
                                                 :above 0}}}
                       :priority 2}]})

(def t2 {:name :t2
         :only-on {:pages :odd
                   :filler :t2-fill}
         :holes [{:name :c
                  :height 50.0
                  :width 80.0
                  :x 10.0
                  :y 10.0
                  :type :text
                  :align {:horizontal :left
                          :vertical :center}
                  :format {:font :times
                           :style #{:regular}
                           :size 12
                           :color [0 0 0]
                           :indent {:all 0}
                           :spacing {:paragraph {:below 0
                                                 :above 0}
                                     :line {:below 0
                                            :above 0}}}
                  :priority 2}]})

(def t3 {:name :t3
         :only-on {:pages :even}
         :holes [{:name :c
                  :height 50.0
                  :width 80.0
                  :x 10.0
                  :y 10.0
                  :type :text
                  :align {:horizontal :left
                          :vertical :center}
                  :format {:font :times
                           :style #{:regular}
                           :size 12
                           :color [0 0 0]
                           :indent {:all 0}
                           :spacing {:paragraph {:below 0
                                                 :above 0}
                                     :line {:below 0
                                            :above 0}}}
                  :priority 2}]})

(def t4 {:name :t4
         :only-on {:pages :odd}
         :holes [{:name :c
                  :height 50.0
                  :width 80.0
                  :x 10.0
                  :y 10.0
                  :type :text
                  :align {:horizontal :left
                          :vertical :center}
                  :format {:font :times
                           :style #{:regular}
                           :size 12
                           :color [0 0 0]
                           :indent {:all 0}
                           :spacing {:paragraph {:below 0
                                                 :above 0}
                                     :line {:below 0
                                            :above 0}}}
                  :priority 2}]})

(def template-pdf-1 "test/templates/transform_only_on/template-1.pdf")

(def context (->> base-context
                  (add-template t1 template-pdf-1)
                  (add-template t1-fill template-pdf-1)
                  (add-template t2 template-pdf-1)
                  (add-template t2-fill template-pdf-1)
                  (add-template t3 template-pdf-1)
                  (add-template t4 template-pdf-1)))

(def pages-1 [{:template :t1
               :locations {:c {:contents {:text "T1"}}}
               :filler-locations {:c {:contents {:text "T1 FILL"}}}}])

(def out-1 (fill-pages pages-1 context))
(.writeTo out-1 (java.io.FileOutputStream. "out/transform-only-on-1.pdf"))
(.close out-1)

(def pages-2 [{:template :t2
               :locations {:c {:contents {:text "T2"}}}
               :filler-locations {:c {:contents {:text "T2 FILL"}}}}])

(def out-2 (fill-pages pages-2 context))
(.writeTo out-2 (java.io.FileOutputStream. "out/transform-only-on-2.pdf"))
(.close out-2)

(def pages-3 [{:template :t3
               :locations {:c {:contents {:text "T3"}}}}
              {:template :t4
               :locations {:c {:contents {:text "T4"}}}}])

(def out-3 (fill-pages pages-3 context))
(.writeTo out-3 (java.io.FileOutputStream. "out/transform-only-on-3.pdf"))
(.close out-3)

