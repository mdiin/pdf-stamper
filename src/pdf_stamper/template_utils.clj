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

;; ## The zipper
;;
;; Since we are looking at trees, we use `clojure.zip` as an efficient way to manipulate the trees.
;; We need some helpers to make the zippers easier to work with in this context.
;;
;; First, we define what a zipper of template parts is.

(defn- parts-zip
  "Create a zipper of parts. This is basically a tree where nodes carry values.
  Every node is potentially a branch, i.e. leaf nodes are just nodes without
  children."
  [root]
  (zip/zipper
    (constantly true)
    ::children
    (fn [node children]
      (assoc node ::children children))
    root))

;; ### Subtrees
;;
;; Since we cannot rely on a regular depth-first traversal of the trees when inserting new parts,
;; we define ways to travel around subtrees.
;;
;; We always add to leaves, and the final templates are constructed from the leaf paths, so an easy
;; way to access leaves is needed.

(defn- to-leaf
  "Go to the left-most leaf in a given subtree."
  [tree]
  (loop [loc tree]
    (if (zip/down loc)
      (recur (zip/down loc))
      loc)))

;; Since leaves can be spread over several subtrees, and `clojure.zip`'s `left` and `right`
;; operations only travel to siblings, some way to find the next subtree that contains leaves
;; is needed.

(defn- next-subtree
  "Returns the loc of the next sutree to insert parts in, or nil
  if there is none."
  [loc]
  (loop [parent loc]
    (if (zip/right parent)
      (zip/right parent)
      (when (zip/prev parent)
        (recur (zip/up parent))))))

(defn- add-to-all-leaves
  "Add the parts to all leaves of tree."
  [tree part & parts]
  ((comp parts-zip zip/root)
    (loop [leaf (to-leaf tree)]
      (let [new-node (reduce (fn [node p]
                               (zip/append-child node p))
                             (zip/append-child leaf part)
                             parts)]
        (if (zip/right new-node)
          (recur (zip/right new-node))
          (if-let [next-subtree (next-subtree new-node)]
            (recur (to-leaf next-subtree))
            new-node))))))

;; ### Parts
;;
;; Parts are separated into *variadic* and *non-variadic* parts. Non-variadic parts
;; are just regular maps and will be merged into the final template as-is.

(defn- add-non-variadic-part
  [trees part]
  (if (seq trees)
    (map (fn [tree]
           (add-to-all-leaves tree {::value part}))
         trees)
    (conj trees (parts-zip {::value part}))))

;; Variadic parts are parts that in the final template can be one of several values. The
;; structure of a variadic part is as follows:
;;
;; ```clojure
;; {:pdf-stamper/name "part"
;;  :pdf-stamper/optional? truthy
;;  :pdf-stamper/variants [{::variant-name "flower" ::variant-part {...}}
;;                         {::variant-name "roots" ::variant-part {...}]}
;; ```
;;
;; Before a variadic part is inserted into the trees, some metadata is added to it. This
;; metadata allows the construction of the final template name from the leaf-root path.

(defn- variadic-part?
  [part]
  (contains? part ::name))

(defn- variadic-parts
  "Construct the final variadic parts, adding metadata fields for template construction."
  [part-name variants optional?]
  (let [parts (map (fn [variant]
                     {::value (with-meta
                                (::variant-part variant)
                                {::name part-name
                                 ::part-name (::variant-name variant)})})
                   variants)]
    (if optional?
      (conj parts {::value (with-meta {} {::name part-name ::part-name ""})})
      parts)))

(defn- add-variadic-part
  "Make a variadic part a child to all leaves in all trees. Creates a new tree if there
  are none."
  [trees part]
  (if (seq trees)
    (map (fn [tree]
           (apply add-to-all-leaves tree (variadic-parts
                                           (::name part)
                                           (::variants part)
                                           (::optional? part))))
         trees)
    (apply conj trees (map parts-zip (variadic-parts
                                       (::name part)
                                       (::variants part)
                                       (::optional? part))))))

(comment
  (add-variadic-part [] {::name "foo"
                         ::optional? false
                         ::variants [{::variant-name "a" ::variant-part {:a 1}}
                                     {::variant-name "b" ::variant-part {:b 1}}]})
  
  (add-variadic-part (add-non-variadic-part [] {:nv 1})
                     {::name "foo"
                      ::optional? false
                      ::variants [{::variant-name "a" ::variant-part {:a 1}}
                                  {::variant-name "b" ::variant-part {:b 1}}]})
  
  (add-variadic-part (add-non-variadic-part [] {:nv 1})
                     {::name "foo"
                      ::optional? true
                      ::variants [{::variant-name "a" ::variant-part {:a 1}}
                                  {::variant-name "b" ::variant-part {:b 1}}]})
  
  (add-variadic-part (add-variadic-part
                       (add-non-variadic-part [] {:nv 1})
                       {::name "foo"
                        ::optional? true
                        ::variants [{::variant-name "a" ::variant-part {:a 1}}
                                    {::variant-name "b" ::variant-part {:b 1}}]})
                     {::name "bar"
                      ::optional? false
                      ::variants [{::variant-name "d" ::variant-part {:d 1}}
                                  {::variant-name "e" ::variant-part {:e 1}}]}))

(defn- add-part
  [trees part]
  (if (variadic-part? part)
    (add-variadic-part trees part)
    (add-non-variadic-part trees part)))

;; ### Paths
;;
;; The paths from leaf to root describe the final templates by merging the value at each
;; node. Values closer to the leaves overwrite values closer to the root in case of conflicts.

(defn tree-paths
  "Construct a vector of **root-leaf** paths for tree. The paths contain only the node values."
  [tree]
  (let [root->leafs (loop [paths []
                           leaf (to-leaf tree)]
                      (let [leaf-path (mapv ::value (zip/path leaf))
                            leaf-value (::value (zip/node leaf))
                            full-path (conj leaf-path leaf-value)]
                        (if (zip/right leaf)
                          (recur (conj paths full-path)
                                 (zip/right leaf))
                          (if-let [next-subtree (next-subtree leaf)]
                            (recur (conj paths full-path)
                                   (to-leaf next-subtree))
                            (conj paths full-path)))))]
    root->leafs))

(defn all-paths
  "Construct a seq of all **root-leaf** paths from all trees."
  [trees]
  (mapcat tree-paths trees))

;; ### Building templates
;;
;; The templates have a naming scheme with holes, which let variadic parts update influence the
;; final template name.

(defn- replace-holes
  [name-with-holes hole-name part-name]
  (if (and hole-name part-name)
    (clojure.string/replace
      name-with-holes
      (re-pattern (str "\\$" hole-name "\\$"))
      part-name)
    name-with-holes))

(defn- merge-hole-bases
  "Merges a seq of hole bases into a single seq of holes. A [hole base] is a vector
  of maps. The result is a single vector of maps."
  [hole-bases & {:keys [?merge-fn ?validation-error-fn]}]
  (let [valid-hole? (if ?validation-error-fn
                      #(schemas/valid-hole? % ?validation-error-fn)
                      schemas/valid-hole?)]
    (into []
          (filter valid-hole?
                  (map (partial apply (or ?merge-fn merge))
                       (vals
                         (group-by :name
                                   (flatten hole-bases))))))))

(defn path-to-template
  "Build a template from a naming scheme and a leaf-root path. Takes an optional
  merge function used when merging two templates."
  [naming-scheme path & {:keys [?merge-fn ?validation-error-fn]}]
  (let [unmerged-holes (reduce (fn [template template-part]
                                 (let [metadata (meta template-part)
                                       part-name (::part-name metadata)
                                       scheme-value (::name metadata)]
                                   (-> template
                                       (update-in [:holes] conj template-part)
                                       (update-in [:name] replace-holes scheme-value part-name))))
                               {:holes []
                                :name naming-scheme}
                               path)
        validation-error-fn (when ?validation-error-fn
                              (partial ?validation-error-fn (:name unmerged-holes)))]
    (-> unmerged-holes
        (update-in [:holes] #(merge-hole-bases % :?merge-fn ?merge-fn :?validation-error-fn validation-error-fn))
        (update-in [:name] keyword))))

(defn parts->trees
  [parts]
  (reduce (fn [trees part]
            (add-part trees part))
          []
          parts))

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
                                                   :pdf-stamper.template-utils/variant-part <holes>}
                                                  {:pdf-stamper.template-utils/variant-name \"roots\"
                                                   :pdf-stamper.template-utils/variant-part <holes>}]}]

  would yield templates with the names:

  [:rhubarbflower :rhubarbroots]

  And the appropriate template parts merged together in the order they are
  specified in the parts vector. <holes> is a vector of hole parts.
  
  Returns a vector of <template.> <template> is a valid template.
  
  ?merge-fn is a function that can merge two maps. The default is to use `clojure.core/merge`.

  ?validation-error-fn is a function that will be called once for every hole that is discarded
  during template construction (due to validation errors). It receives a template name and a
  validation error."
  [naming-scheme parts & {:keys [?merge-fn ?validation-error-fn] :as opts}]
  (let [naming-scheme-replacement-map (into {} (map (comp vec reverse)
                                                    (re-seq #"\$([^\$]+)\$" naming-scheme)))]
    (map (fn [path]
           (path-to-template naming-scheme path :?merge-fn ?merge-fn :?validation-error-fn ?validation-error-fn))
         (mapcat tree-paths (parts->trees parts)))))

