package pro.fessional.meepo.jmh;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.ThreadParams;
import pro.fessional.meepo.sack.Gene;
import pro.fessional.meepo.sack.Holder;
import pro.fessional.meepo.sack.Parser;

import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Share one parsed gene across workers with separate request contexts and outputs.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class MeepoConcurrency {
    @Param({ "placeholder", "rows20", "rows200" })
    public String workload;
    @Param({ "string", "writer", "utf8", "mixed" })
    public String api;

    private static final Charset GBK = Charset.forName("GBK");
    private Gene gene;
    private int rows;

    @Setup
    public void setup() {
        rows = workload.equals("rows200") ? 200 : 20;
        gene = workload.equals("placeholder") ? Holder.parse("Hello {{owner}}\n") : Parser.parse(
            "# HI-MEEPO\n# RNA:EACH /1/items/item\n# RNA:USE /OWNER/owner/*\n"
            + "# RNA:USE /ITEM/item/*\n# RNA:USE /COUNT/item._count/*\n"
            + "# RNA:WHEN /yes/flag/choice\nOWNER:COUNT:ITEM:Y\n"
            + "# RNA:ELSE choice\nOWNER:COUNT:ITEM:N\n# RNA:DONE choice\n# RNA:DONE item\n");
    }

    @State(Scope.Thread)
    public static class Request {
        final Map<String, Object> context = new HashMap<>();
        String expected;
        int sequence;

        @Setup
        public void setup(MeepoConcurrency shared, ThreadParams thread) {
            initialize(shared, thread.getThreadIndex());
        }

        void initialize(MeepoConcurrency shared, int identity) {
            String owner = "worker-" + identity + "中文😀";
            // Keep the branch distribution identical for every worker count.
            boolean flag = true;
            List<Integer> items = new ArrayList<>();
            StringBuilder output = new StringBuilder();
            for (int item = 0; item < shared.rows; item++) {
                items.add(item);
                output.append(owner).append(':').append(item + 1).append(':').append(item)
                      .append(flag ? ":Y\n" : ":N\n");
            }
            context.put("owner", owner);
            context.put("items", items);
            context.put("flag", flag);
            expected = shared.workload.equals("placeholder") ? "Hello " + owner + "\n" : output.toString();
            if (!expected.equals(shared.gene.merge(context))) throw new IllegalStateException("String mismatch");
            for (Charset charset : new Charset[]{ StandardCharsets.UTF_8, GBK }) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                shared.gene.merge(context, bytes, charset);
                if (!Arrays.equals(expected.getBytes(charset), bytes.toByteArray()))
                    throw new IllegalStateException("Byte output mismatch for " + charset);
            }
        }
    }

    @Benchmark
    public Object render(Request request) {
        if (api.equals("string")) return gene.merge(request.context);
        if (api.equals("writer")) {
            StringWriter output = new StringWriter(4096);
            gene.merge(request.context, output);
            return output;
        }
        Charset charset = api.equals("mixed") && (request.sequence++ & 1) != 0 ? GBK : StandardCharsets.UTF_8;
        ByteArrayOutputStream output = new ByteArrayOutputStream(4096);
        gene.merge(request.context, output, charset);
        return output.toByteArray();
    }
}
