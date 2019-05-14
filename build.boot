(task-options!
 pom {:project     'com.clojure-goes-fast/jvm-alloc-rate-meter
      :version     "0.1.2"
      :description "Measure JVM heap allocation rate in real time"
      :url         "http://github.com/clojure-goes-fast/jvm-alloc-rate-meter"
      :scm         {:url "http://github.com/clojure-goes-fast/jvm-alloc-rate-meter"}
      :license     {"Eclipse Public License"
                    "http://www.eclipse.org/legal/epl-v10.html"}}
 push {:repo "clojars"})

(set-env! :source-paths #{"src"})

(deftask build []
  (comp (javac)
        (sift :to-resource [#"jvm_alloc_rate_meter/.+\.clj$"])
        (pom) (jar)))

(comment ;; Development
  (set-env! :dependencies #(conj % '[virgil "LATEST"]))
  (require '[virgil.boot :as virgil])
  (boot (virgil/javac* :verbose true)))
