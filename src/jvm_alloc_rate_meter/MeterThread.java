package jvm_alloc_rate_meter;

import com.sun.management.ThreadMXBean;
import java.math.BigInteger;
import java.util.List;
import java.util.function.LongConsumer;
import java.lang.management.MemoryMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;

public class MeterThread extends Thread {

    private final MemoryMXBean memoryBean;
    private final ThreadMXBean threadBean;
    private final List<GarbageCollectorMXBean> gcBeans;

    private final LongConsumer callback;
    private final int intervalMs;

    private volatile boolean doRun = true;

    public MeterThread(LongConsumer callback) {
        this(callback, 1000);
    }

    public MeterThread(LongConsumer callback, int intervalMs) {
        super("jvm-alloc-rate-meter-thread");

        this.memoryBean = ManagementFactory.getMemoryMXBean();
        this.threadBean = (ThreadMXBean)ManagementFactory.getThreadMXBean();
        this.gcBeans = ManagementFactory.getGarbageCollectorMXBeans();

        this.callback = callback;
        this.intervalMs = intervalMs;
        setDaemon(true);
    }

    // Basically, we have two ways of measuring the allocation rate.
    // The first relies on checking heap usage between two points in time and
    // subtracting. This is accurate but works only if the GC didn't trigger in
    // the meantime.
    // The second works by taking allocation stats by each alive thread. This is
    // more reliable but potentially less accurate (because threads can go away,
    // and we lose their allocation stats).
    // The idea is to use the first approach if GC didn't happen, and the second
    // one if it did.

    public void run() {
        long lastTime = 0, lastHeapUsage = -1, lastGcCounts = -1;
        BigInteger lastThreadAllocated = BigInteger.valueOf(-1);
        try {
            while (doRun) {
                long time = System.currentTimeMillis();
                double multiplier = 1000.0 / (time - lastTime);

                long heapUsage = usedHeap();
                long gcCounts = gcCounts();
                long deltaUsage = heapUsage - lastHeapUsage;

                BigInteger threadAllocated = allocatedByAllThreads();
                BigInteger deltaAllocated = threadAllocated.subtract(lastThreadAllocated);

                if (lastTime != 0) {
                    if ((gcCounts == lastGcCounts) && (deltaUsage >= 0)) {
                        long rate = Math.round(deltaUsage * multiplier);
                        callback.accept(rate);
                    } else if (threadAllocated.compareTo(BigInteger.ZERO) >= 0 &&
                               lastThreadAllocated.compareTo(BigInteger.ZERO) >= 0 &&
                               deltaAllocated.compareTo(BigInteger.ZERO) >= 0) {
                        long rate = Math.round(deltaAllocated.longValue() * multiplier);
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

    private long usedHeap() {
        return memoryBean.getHeapMemoryUsage().getUsed();
    }

    /** Returns total number of GC cycles since the start of the VM. **/
    private long gcCounts() {
        long total = 0;
        for (GarbageCollectorMXBean bean : gcBeans) {
            total += bean.getCollectionCount();
        }
        return total;
    }

    /** Total allocation can overflow a long, so using BigInt here. **/
    private BigInteger allocatedByAllThreads() {
        long[] ids = threadBean.getAllThreadIds();
        long[] allocatedBytes = threadBean.getThreadAllocatedBytes(ids);
        BigInteger result = BigInteger.ZERO;
        // This is approach is not entirely correct because doesn't see the
        // allocation data from threads that died since the last iteration. Oh
        // well, doing best effort.
        for (long abytes : allocatedBytes)
            result = result.add(BigInteger.valueOf(abytes));
        return result;
    }
}
