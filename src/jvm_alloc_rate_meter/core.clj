(ns jvm-alloc-rate-meter.core
  (:import jvm_alloc_rate_meter.MeterThread
           java.util.function.LongConsumer))

(defn start-alloc-rate-meter
  "Start the allocation meter thread. It will measure the heap object allocation
  rate every `interval-ms` (1000 by default) and call `callback-fn` with the
  allocation rate in bytes/sec. Note that the rate will not be reported every
  `interval-ms` as the iterations that happen accross a GC cycle are discarded.

  Returns a nullary function which when called terminates the measuring thread."
  [callback-fn & {:keys [interval-ms]}]
  (let [iw (MeterThread. (reify LongConsumer
                           (accept [_ v]
                             (callback-fn v)))
                         (or interval-ms 1000))]
    (.start iw)
    #(.terminate iw)))

#_(def -t (start-alloc-rate-meter #(println (/ % 1e6) "MB/sec")))
