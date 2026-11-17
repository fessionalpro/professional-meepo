package pro.fessional.meepo.sack;

import org.junit.jupiter.api.Test;

import pro.fessional.meepo.TestingHelper;

import java.io.*;
import java.lang.ref.SoftReference;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

import static org.junit.jupiter.api.Assertions.*;

class GeneTest {
    @Test
    void sharedControlPlanKeepsConcurrentContextsAndCharsetsIsolated() throws Exception {
        String source = "# HI-MEEPO\n# RNA:EACH /1/items/item\n"
                        + "# RNA:USE /OWNER/owner/*\n# RNA:USE /ITEM/item/*\n# RNA:USE /COUNT/item._count/*\n"
                        + "# RNA:WHEN /yes/flag/choice\nOWNER:COUNT:ITEM:Y\n"
                        + "# RNA:ELSE choice\nOWNER:COUNT:ITEM:N\n# RNA:DONE choice\n# RNA:DONE item\n";
        Gene gene = Parser.parse(source);
        CyclicBarrier start = new CyclicBarrier(8);
        ExecutorService workers = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> results = new ArrayList<>();
            for (int thread = 0; thread < 8; thread++) {
                final int identity = thread;
                results.add(workers.submit(() -> {
                    Charset charset = Charset.forName(new String[]{ "UTF-8", "GBK", "UTF-16", "ISO-2022-JP" }[identity % 4]);
                    List<String> items = Arrays.asList("left😀", "right中", "");
                    Map<String, Object> context = new HashMap<>();
                    context.put("items", items);
                    context.put("flag", identity < 4);
                    start.await(10, TimeUnit.SECONDS);
                    for (int iteration = 0; iteration < 200; iteration++) {
                        String owner = "worker-" + identity + ":" + iteration + "中😀\ud800";
                        context.put("owner", owner);
                        StringBuilder expected = new StringBuilder();
                        for (int item = 0; item < items.size(); item++) {
                            expected.append(owner).append(':').append(item + 1).append(':')
                                    .append(items.get(item)).append(identity < 4 ? ":Y\n" : ":N\n");
                        }
                        String value = expected.toString();
                        assertEquals(value, gene.merge(context));
                        StringWriter writer = new StringWriter();
                        gene.merge(context, writer);
                        assertEquals(value, writer.toString());
                        assertArrayEquals(value.getBytes(charset), TestingHelper.bytes(gene, context, charset));
                        assertEquals(3, context.size());
                        assertEquals(owner, context.get("owner"));
                        assertSame(items, context.get("items"));
                    }
                    return null;
                }));
            }
            for (Future<?> result : results) result.get(30, TimeUnit.SECONDS);
        }
        finally {
            workers.shutdownNow();
        }
    }

    @Test
    void concurrentReadersRemainSafeWhenTheSoftCacheIsEvicted() throws Exception {
        Gene gene = Parser.parse("cached 中文🙂");
        gene.merge(Collections.emptyMap());
        Field cache = Gene.class.getDeclaredField("text9");
        cache.setAccessible(true);
        CyclicBarrier start = new CyclicBarrier(4);
        ExecutorService workers = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Void>> jobs = new ArrayList<>();
            for (int thread = 0; thread < 4; thread++) {
                jobs.add(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    for (int i = 0; i < 2000; i++) {
                        ((SoftReference<?>) cache.get(gene)).clear();
                        assertEquals("cached 中文🙂", gene.merge(Collections.emptyMap()));
                    }
                    return null;
                });
            }
            for (Future<Void> future : workers.invokeAll(jobs, 30, TimeUnit.SECONDS)) future.get();
        }
        finally {
            workers.shutdownNow();
        }
    }

    @Test
    void recursiveRenderingDoesNotShareOrOverwriteTheOuterBuffer() {
        Gene inner = Holder.parse("inner {{value}}");
        Gene outer = Holder.parse("before [{{nested}}] after");
        Map<String, Object> input = Collections.singletonMap("nested",
            (Supplier<String>) () -> inner.merge(Collections.singletonMap("value", "中文🙂"), 1));
        assertEquals("before [inner 中文🙂] after", outer.merge(input, 1));
        assertEquals(1, input.size());
    }

    @Test
    void callerFunctionOverridesIncludingNullKeepPrecedence() {
        Gene gene = Holder.parse("{{value | upperCase}}");
        Map<String, Object> input = new HashMap<>();
        input.put("value", "text");
        input.put("fun:upperCase", (Function<Object, Object>) value -> "custom");
        assertEquals("custom", gene.merge(input));
        input.put("upperCase", (Function<Object, Object>) value -> "raw");
        assertEquals("raw", gene.merge(input));
        input.remove("upperCase");
        input.put("fun:upperCase", null);
        assertEquals("", gene.merge(input));
    }

    @Test
    void geneByteOutputMatchesStringsAcrossFragmentBoundaries() {
        List<Gene> genes = Arrays.asList(Parser.parse("静态😀"), Parser.parse("静态\ud800"), Holder.parse("静态\ud83d{{value}}"),
            Holder.parse("{{value}}\ude00尾"), Holder.parse("{{value}}"), Parser.parse(""));
        for (Gene gene : genes)
            for (String value : Arrays.asList("\ude00", "\ud83d", "中文😀", "")) {
                Map<String, Object> context = Collections.singletonMap("value", value);
                byte[] expected = bytes(gene.merge(context));
                assertArrayEquals(expected, TestingHelper.bytes(gene, context));
            }
        Gene big = Holder.parse("{{value}}");
        String text = String.join("", Collections.nCopies(20000, "中文😀"));
        assertEquals(text, big.merge(Collections.singletonMap("value", text)));
        assertArrayEquals(bytes(text), TestingHelper.bytes(big, Collections.singletonMap("value", text)));
        assertEquals("x", big.merge(Collections.singletonMap("value", "x")));
        Gene dangling = Holder.parse("{{value}}");
        OutputStream failure = new OutputStream() {
            public void write(int value) throws IOException {
                throw new IOException("finish");
            }
        };
        assertThrows(IllegalStateException.class, () -> dangling.merge(Collections.singletonMap("value", "\ud800"), failure));
        class Stream extends ByteArrayOutputStream {
            boolean closed;
            boolean flushed;

            @Override
            public void close() {
                closed = true;
            }

            @Override
            public void flush() {
                flushed = true;
            }
        }
        Stream stream = new Stream();
        big.merge(Collections.singletonMap("value", "😀"), stream);
        assertArrayEquals(bytes("😀"), stream.toByteArray());
        assertFalse(stream.closed);
        assertFalse(stream.flushed);
    }

    @Test
    void sharedTemplatePublishesStaticByteCachesSafely() throws Exception {
        Gene gene = Holder.parse("中文😀={{value}}尾😀");
        ExecutorService workers = Executors.newFixedThreadPool(4);
        try {
            List<Future<?>> pending = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                final String value = "thread" + i + "😀";
                pending.add(workers.submit(() -> {
                    Map<String, Object> context = Collections.singletonMap("value", value);
                    for (int j = 0; j < 100; j++)
                        assertArrayEquals(bytes("中文😀=" + value + "尾😀"), TestingHelper.bytes(gene, context));
                }));
            }
            for (Future<?> result : pending) result.get();
        }
        finally {
            workers.shutdownNow();
        }
    }

    @Test
    void binaryFormattingMatchesStringsForAllSupportedValueShapes() {
        Gene gene = Holder.parse("[{{value}}]");
        Object[] values = { null, true, false, Integer.MIN_VALUE, Long.MIN_VALUE, -0D, Double.NaN,
                            Double.POSITIVE_INFINITY, Float.MIN_VALUE, (short) -7, (byte) -8, '中',
                            new BigDecimal("1E+30"), new StringBuilder("中文😀"), new char[]{ '\ud83d', '\ude00' },
                            new int[]{ 1, 2, 3 }, Arrays.asList("\ud83d", null, "\ude00", 42),
                            new Object() {
                                @Override
                                public String toString() {
                                    return "object😀";
                                }
                            } };
        for (Object value : values) {
            Map<String, Object> context = Collections.singletonMap("value", value);
            assertArrayEquals(bytes(gene.merge(context)), TestingHelper.bytes(gene, context));
        }
    }

    @Test
    void binaryOutputIncludesRawAndReplacedStaticPieces() {
        Gene gene = Parser.parse("# HI-MEEPO\n# DNA:RAW raw😀\n# DNA:SET /OLD/NEW😀/*\n# RNA:USE /VAL/value/*\nOLD VAL\n");
        Map<String, Object> context = Collections.singletonMap("value", "动态😀");
        String text = gene.merge(context);
        assertTrue(text.contains("raw😀"));
        assertTrue(text.contains("NEW😀"));
        assertTrue(text.contains("动态😀"));
        assertArrayEquals(bytes(text), TestingHelper.bytes(gene, context));
    }

    @Test
    void concurrentCapacityEstimatesNeverChangeOutput() throws Exception {
        Gene gene = Holder.parse("{{value}}");
        ExecutorService workers = Executors.newFixedThreadPool(4);
        try {
            List<Future<?>> pending = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                final String value = String.join("", Collections.nCopies(i == 0 ? 1 : i * 1000, "中文😀"));
                pending.add(workers.submit(() -> {
                    Map<String, Object> context = Collections.singletonMap("value", value);
                    for (int j = 0; j < 30; j++) {
                        assertEquals(value, gene.merge(context));
                        assertArrayEquals(bytes(value), TestingHelper.bytes(gene, context));
                    }
                }));
            }
            for (Future<?> result : pending) result.get();
        }
        finally {
            workers.shutdownNow();
        }
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
