package pro.fessional.meepo.jmh;

import java.lang.management.ManagementFactory;

/**
 * Allocation counts are read on the rendering thread before it terminates.
 */
public class RenderProbe {
    private static volatile String sink;

    public static void main(String[] args) throws Exception {
        EngineComparison comparison = new EngineComparison();
        comparison.engine = "meepo";
        comparison.workload = args[0];
        if (args.length > 2) comparison.api = args[2];
        comparison.setup();
        int count = Integer.parseInt(args[1]);
        for (int i = 0; i < Math.min(count, 100000); i++) sink = comparison.render();
        com.sun.management.ThreadMXBean bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        if (!bean.isThreadAllocatedMemoryEnabled()) bean.setThreadAllocatedMemoryEnabled(true);
        long thread = Thread.currentThread().getId();
        long bytes = bean.getThreadAllocatedBytes(thread);
        long start = System.nanoTime();
        for (int i = 0; i < count; i++) sink = comparison.render();
        long elapsed = System.nanoTime() - start;
        bytes = bean.getThreadAllocatedBytes(thread) - bytes;
        System.out.printf(java.util.Locale.ROOT, "%s: %.1f ns/op, %.1f B/op, output=%d chars%n",
            comparison.workload, (double) elapsed / count, (double) bytes / count, sink.length());
    }
}
