;; In some situations, templates in pdf-stamper can become difficult to maintain. One such
;; situation can occur when you have a number of template parts that combine with each other to
;; form the final templates. If the template parts form semantic "layers", and each part of
;; a layer needs to be combined with all parts of the following layer, we get an exponential
;; explosion in the number of templates. Since there is a direction in the way parts are combined
;; the final templates can be described by a number of trees, where each path from a leaf to the
;; root describes one template.
;;
;; To avoid having to write an exponential number of template descriptions by hand, this namespace
;; provides utilities that allow you to specify how the semantic layers relate to each other.

(ns pdf-stamper.template-utils
  (:require
    [pdf-stamper.schemas :as schemas]

    [clojure.zip :as zip]
    ))

(defn sub-roots
  "Computes the roots of all sub trees of tree."
  [tree]
  (if-let [variants (::variants tree)]
    (let [roots (->> variants
                     (map #(clojure.set/rename-keys % {::variant-name :name
                                                       ::variant-part :part}))
                     (map #(assoc % :key (::name tree)))
                     (#(if (::optional? tree)
                         (conj % {:key (::name tree) :name ""})
                         %)))]
      roots)
    [{:part tree}]))

(defn tree-maker
  "Builds a tree where each path to a leaf determines a template name and the
  partial templates to use for assembling the template."
  ([[root & descendants :as parts]]
   (if (keyword? root)
     (tree-maker {:part root} descendants)
     (tree-maker {:part ::not-a-part} parts)))

  ([root [child & grand-children]]
   (if (seq grand-children)
     (let [child-level-roots (sub-roots child)
           sub-root (first child-level-roots)
           subtrees (:subtrees (tree-maker sub-root grand-children))

           final-child-level-roots (map #(assoc % :subtrees subtrees) child-level-roots)]
       (assoc root
              :subtrees final-child-level-roots))
     (assoc root :subtrees (sub-roots child)))))

(defn- replace-holes
  [name-with-holes hole-name part-name]
  (if (and hole-name part-name)
    (clojure.string/replace
      name-with-holes
      (re-pattern (str "\\$" hole-name "\\$"))
      part-name)
    name-with-holes))

(defn merge-parts
  [f & parts]
  (into []
        (map (partial apply f)
             (vals
               (group-by :name
                         (flatten parts))))))

(defn add-parts
  [parts ps]
  (if ps
    (if (vector? ps)
      (into parts ps)
      (conj parts ps))
    parts))

(defn process-node
  [acc-val next-val]
  (-> acc-val
      (update :name replace-holes (:key next-val) (:name next-val))
      (update :parts add-parts (:part next-val))))

(defn paths
  "Given a tree and an initial structure for the result, builds the result as a
  map of :name and :parts."
  [tree s]
  (let [tree-name (:name tree)]
    (if-let [subtrees (:subtrees tree)]
      (pmap #(paths % (process-node s tree)) subtrees)
      (-> (process-node s tree)
          (update :name keyword)))))

(defn make-templates
  "Naming scheme is a keyword with \"holes\" defined by $hole-name$. Example
  naming scheme:

  :rhubarb$part$

  Values inbetween $'s are matched to the :name of individual parts and replaced
  as needed. Example with the above naming scheme:

  parts = [<holes>
           {:pdf-stamper.template-utils/name \"part\"
            :pdf-stamper.template-utils/optional? true
            :pdf-stamper.template-utils/variants [{:pdf-stamper.template-utils/variant-name \"flower\"
                                                   :pdf-stamper.template-utils/variant-part <parts>}
                                                  {:pdf-stamper.template-utils/variant-name \"roots\"
                                                   :pdf-stamper.template-utils/variant-part <parts>}]}]

  would yield templates with the names:

  [:rhubarbflower :rhubarbroots]

  And the appropriate template parts merged together in the order they are
  specified in the parts vector. <parts> is either a single template part name, or
  a vector of template part names.

  Returns a vector of <template>. <template> is a valid template: It contains the `:name` of the
  template and a the `:parts`."
  [naming-scheme parts]
  (-> parts
      (tree-maker)
      (paths {:name naming-scheme :parts []})
      (flatten)))
