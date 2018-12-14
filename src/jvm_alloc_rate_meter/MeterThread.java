package jvm_alloc_rate_meter;

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

    public void run() {
        double multiplier = 1000.0 / intervalMs;
        try {
            long prevUsage = -1, prevGcCounts = -1;
            while (doRun) {
                long usage = usedHeap();
                long gcCounts = gcCounts();

                if ((gcCounts == prevGcCounts) && (usage >= prevUsage)) {
                    long rate = Math.round((usage - prevUsage) * multiplier);
                    callback.accept(rate);
                }

                Thread.sleep(intervalMs);

                prevUsage = usage;
                prevGcCounts = gcCounts;
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
}
