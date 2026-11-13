package pro.fessional.meepo.jmh;

import java.lang.management.ManagementFactory;

/**
 * Read allocation counters on the rendering thread before it exits (-f 0 JMH cannot do this reliably).
 */
public class AllocationProbe {
    private static volatile Object sink;

    public static void main(String[] args) throws Exception {
        TemplateComparison state = new TemplateComparison();
        state.engine = args[0];
        state.workload = args[1];
        state.api = args[2];
        int iterations = Integer.parseInt(args[3]);
        try {
            state.setup();
            for (int i = 0; i < 50000; i++) sink = state.render();
            var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
            bean.setThreadAllocatedMemoryEnabled(true);
            long thread = Thread.currentThread().getId();
            long before = bean.getThreadAllocatedBytes(thread);
            for (int i = 0; i < iterations; i++) sink = state.render();
            long after = bean.getThreadAllocatedBytes(thread);
            System.out.println("ALLOCATED_BYTES_PER_OP=" + (after - before) / (double) iterations);
        }
        finally {
            state.cleanup();
        }
    }
}
