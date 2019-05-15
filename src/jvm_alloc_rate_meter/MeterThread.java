package jvm_alloc_rate_meter;

import com.sun.management.ThreadMXBean;
import java.math.BigInteger;
import java.util.function.LongConsumer;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;

public class MeterThread extends Thread {

    private LongConsumer callback;
    private int intervalMs;

    private volatile boolean doRun = true;

    public MeterThread(LongConsumer callback) {
        this(callback, 1000);
    }

    public MeterThread(LongConsumer callback, int intervalMs) {
        super("jvm-alloc-rate-meter-thread");
        this.callback = callback;
        this.intervalMs = intervalMs;
        setDaemon(true);
    }

    // Basically, we have two ways of measuring the allocation rate.
    // First relies on checking heap usage between two points of time and
    // substracting. This is accurate, but works only if the GC didn't trigger
    // in the meantime.
    // Second works by taking allocation stats by each alive thread. This is
    // more reliable, but potentially less accurate (because threads can go
    // away, and we lose their allocation stats).
    // The idea is to use the first approach if GC didn't happen, and the second
    // one if it did.

    public void run() {
        long lastTime = 0, lastHeapUsage = -1, lastGcCounts = -1;
        BigInteger lastThreadAllocated = BigInteger.valueOf(-1);
        try {
            while (doRun) {
                long heapUsage = usedHeap();
                long gcCounts = gcCounts();
                BigInteger threadAllocated = allocatedByAllThreads();
                long time = System.currentTimeMillis();

                double multiplier = 1000.0 / (time - lastTime);
                long deltaUsage = heapUsage - lastHeapUsage;

                if (lastTime != 0) {
                    if ((gcCounts == lastGcCounts) && (deltaUsage >= 0)) {
                        long rate = Math.round(deltaUsage * multiplier);
                        callback.accept(rate);
                    } else if (threadAllocated.compareTo(BigInteger.ZERO) >= 0 &&
                               lastThreadAllocated.compareTo(BigInteger.ZERO) >= 0 &&
                               threadAllocated.compareTo(BigInteger.ZERO) >= 0) {
                        long rate = Math.round(threadAllocated.subtract(lastThreadAllocated).longValue() * multiplier);
                        callback.accept(rate);
                    } else {
                        // Apparently, neither approach did well, just skip this
                        // iteration.
                    }
                }

                Thread.sleep(intervalMs);

                lastTime = time;
                lastHeapUsage = heapUsage;
                lastGcCounts = gcCounts;
                lastThreadAllocated = threadAllocated;
            }
        } catch (InterruptedException e) {
            System.err.println("MeterThread terminating...");
        }
    }

    public void terminate() {
        doRun = false;
    }

    private static long usedHeap() {
        return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
    }

    /** Returns total number of GC cycles since the start of the VM. **/
    private static long gcCounts() {
        long total = 0;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            total += bean.getCollectionCount();
        }
        return total;
    }

    /** Total allocation can overflow a long, so using BigInt here. **/
    private static BigInteger allocatedByAllThreads() {
        ThreadMXBean bean = (ThreadMXBean)ManagementFactory.getThreadMXBean();
        long[] ids = bean.getAllThreadIds();
        long[] allocatedBytes = bean.getThreadAllocatedBytes(ids);
        BigInteger result = BigInteger.ZERO;
        // This is not correct because we will lose allocation data from threads
        // that died. Oh well.
        for (long abytes : allocatedBytes)
            result = result.add(BigInteger.valueOf(abytes));
        return result;
    }
}
