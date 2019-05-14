# jvm-alloc-rate-meter

A trivial Java library for continuous monitoring of JVM heap allocation rate.
Many GCs can report this metric in their logs, but it's hard to extract that in
real time. This library solves that.

jvm-alloc-rate-meter starts a background thread that measures how heap usage
changes between the specified intervals and reports the result into a callback.
You get to choose where the data will go — log it, send to a monitoring solution
(Metrics, StatsD, etc.), or aggregate some other way.

## Usage

First, add it to your dependencies:

[![](https://clojars.org/com.clojure-goes-fast/jvm-alloc-rate-meter/latest-version.svg)](https://clojars.org/com.clojure-goes-fast/jvm-alloc-rate-meter)

### Java

You are safe to use this library in any Java project, it doesn't explicitly
depend on Clojure (or anything else). You can also forgo the dependency and just
paste [MeterThread](src/jvm_alloc_rate_meter/MeterThread.java) class directly
into your project.

```java
import jvm_alloc_rate_meter.MeterThread;

// ...

MeterThread t = new MeterThread((r) -> System.out.println("Rate is: " + (r / 1e6) + " MB/sec"));

// To stop the meter thread
t.terminate();
```

The first argument to MeterThread's constructor is a LongConsumer callback that
will be called with every new measurement of allocation rate in bytes/sec.

The second (optional) argument is the measurement interval in milliseconds (1000
by default). Regardless of the interval length, the reported rate will be
normalized to per-second value. Note that the callback might not be called on
each interval, because under certain conditions we can't reliably measure the
allocation rate (if both the garbage collection happened and a few of the
heavy-allocating threads died).

Here's an example of combining jvm-alloc-rate-meter with [Dropwizard
Metrics](https://github.com/dropwizard/metrics):

```java
import jvm_alloc_rate_meter.MeterThread;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.SlidingTimeWindowArrayReservoir;
import java.util.concurrent.TimeUnit;

// ...

Histogram hist = new Histogram(new SlidingTimeWindowArrayReservoir(10, TimeUnit.SECONDS));
MeterThread mt = new MeterThread(hist::update);
mt.start()

// Now, you can forward this histogram to Graphite, or check the values manually, e.g.:
hist.getSnaphot.getMean();
```

### Clojure

```clj
(require '[jvm-alloc-rate-meter.core :as ameter])
(def am (ameter/start-alloc-rate-meter #(println "Rate is:" (/ % 1e6) "MB/sec")))

;; Test it out
(while true
  (byte-array 1e7)
  (Thread/sleep 100))

;; The meter should report ~100 MB/sec allocation rate into the console.

;; To stop the meter thread
(am)
```

The only function `start-alloc-rate-meter` accepts an unary function as an
argument and starts the measuring thread. See the section above using the
library from Java for the details.

## License

jvm-hiccup-meter is distributed under the Eclipse Public License. See
[LICENSE](LICENSE).

Copyright 2018 Alexander Yakushev
