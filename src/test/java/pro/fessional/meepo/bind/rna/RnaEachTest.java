package pro.fessional.meepo.bind.rna;

import org.junit.jupiter.api.Test;

import pro.fessional.meepo.TestingHelper;
import pro.fessional.meepo.eval.JavaEval;
import pro.fessional.meepo.sack.Gene;
import pro.fessional.meepo.sack.Parser;

import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.BaseStream;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class RnaEachTest {
    @Test
    void cursorsMatchCollectionValuesAndMetadataForEveryDirectionAndStride() {
        List<Object> data = Arrays.asList("a", null, "c", "d", null, "f", "g", null, "i");
        for (int step : new int[]{ 1, 2, 3, 5, Integer.MAX_VALUE, -1, -2, -3, Integer.MIN_VALUE }) {
            Gene gene = template(step);
            for (int length = 0; length <= data.size(); length++) {
                List<Object> values = data.subList(0, length);
                List<List<Object>> expected = new ArrayList<>();
                List<List<Object>> expectedAfter = new ArrayList<>();
                String output = gene.merge(model(values, expected, expectedAfter));
                List<Supplier<Object>> factories = Arrays.asList(values::iterator,
                    () -> (Iterable<Object>) values::iterator, values::stream, values::parallelStream);
                for (Supplier<Object> factory : factories) {
                    Object source = factory.get();
                    List<List<Object>> frames = new ArrayList<>();
                    List<List<Object>> after = new ArrayList<>();
                    Map<String, Object> model = model(source, frames, after);
                    Map<String, Object> before = new HashMap<>(model);
                    try {
                        assertEquals(output, gene.merge(model), source.getClass() + " step=" + step + " length=" + length);
                        assertEquals(expected, frames);
                        assertEquals(expectedAfter, after);
                        assertEquals(before, model);
                    }
                    finally {
                        if (source instanceof BaseStream) ((BaseStream<?, ?>) source).close();
                    }
                }
            }
        }
    }

    @Test
    void emptyCursorsSelectElseAndResetMetadataWithoutOverwritingTheItem() {
        List<Object> empty = Collections.emptyList();
        for (int step : new int[]{ 2, -2, Integer.MIN_VALUE }) {
            List<Object> sources = Arrays.asList(empty.iterator(), (Iterable<Object>) empty::iterator,
                Stream.empty(), IntStream.empty());
            for (Object source : sources) {
                List<List<Object>> frames = new ArrayList<>();
                List<List<Object>> after = new ArrayList<>();
                assertEquals("empty\n", template(step).merge(model(source, frames, after)));
                assertTrue(frames.isEmpty());
                assertEquals(Collections.singletonList(Arrays.asList("caller", 0, 0, false, false)), after);
            }
        }
    }

    @Test
    void primitiveStreamsAndByteOutputUseTheSameCollectionTraversal() {
        for (int step : new int[]{ 2, -2 }) {
            List<Object> sources = Arrays.asList(IntStream.rangeClosed(1, 5).parallel(),
                LongStream.rangeClosed(1, 5), DoubleStream.of(1, 2, 3, 4, 5));
            for (Object source : sources) {
                String expected = step > 0 ? "1\n3\n5\n" : "5\n3\n1\n";
                if (source instanceof DoubleStream) expected = expected.replace("\n", ".0\n");
                assertEquals(expected, template(step).merge(model(source, new ArrayList<>(), new ArrayList<>())));
            }
        }
        Charset gbk = Charset.forName("GBK");
        Map<String, Object> model = model(Stream.of("甲", "乙", "丙"), new ArrayList<>(), new ArrayList<>());
        assertArrayEquals("甲\n丙\n".getBytes(gbk), TestingHelper.bytes(template(2), model, gbk));
    }

    @Test
    void reverseCursorSelectionIsAnchoredAtTheLastRemainingInputIndex() {
        List<String> values = Arrays.asList("a", "b", "c", "d");
        assertEquals("d\nb\n", template(-2).merge(model(values.iterator(), new ArrayList<>(), new ArrayList<>())));
        assertEquals("d\na\n", template(-3).merge(model(values.iterator(), new ArrayList<>(), new ArrayList<>())));
        assertEquals("d\n", template(Integer.MIN_VALUE).merge(model(values.iterator(), new ArrayList<>(), new ArrayList<>())));
        Iterator<String> remaining = values.iterator();
        remaining.next();
        List<List<Object>> frames = new ArrayList<>();
        assertEquals("d\nb\n", template(-2).merge(model(remaining, frames, new ArrayList<>())));
        assertEquals(Arrays.asList("d", 3, 1, true, false), frames.get(0));
        assertEquals(Arrays.asList("b", 3, 2, false, true), frames.get(1));
        AtomicInteger opened = new AtomicInteger();
        Iterable<String> iterable = () -> {
            opened.incrementAndGet();
            return values.iterator();
        };
        Gene muted = Parser.parse("# HI-MEEPO\n# RNA:EACH map!/-2/items/it\n"
                                  + "# RNA:USE /VALUE/it/*\nVALUE\n# RNA:DONE it\n");
        assertEquals("d\nb\n", muted.merge(Collections.singletonMap("items", iterable)));
        assertEquals(1, opened.get());
        AtomicInteger closed = new AtomicInteger();
        try (Stream<Integer> stream = Stream.of(1, 2, 3).onClose(closed::incrementAndGet)) {
            assertEquals("3\n2\n1\n", template(-1).merge(model(stream, new ArrayList<>(), new ArrayList<>())));
            assertEquals(0, closed.get());
        }
        assertEquals(1, closed.get());
    }

    @Test
    void cursorsAreExhaustedBeforeRenderingAndCountsHaveCollectionTypes() {
        AtomicInteger consumed = new AtomicInteger();
        Stream<Integer> stream = IntStream.range(0, 5).boxed().peek(value -> consumed.incrementAndGet());
        List<List<Object>> frames = new ArrayList<>();
        Map<String, Object> model = model(stream, frames, new ArrayList<>());
        model.put("capture", (JavaEval) (ctx, previous, args) -> {
            assertEquals(5, consumed.get());
            assertInstanceOf(Integer.class, ctx.get("it._total"));
            assertInstanceOf(Integer.class, ctx.get("it._count"));
            frames.add(state(ctx));
            return null;
        });
        assertEquals("0\n2\n4\n", template(2).merge(model));
        assertEquals(Arrays.asList(Arrays.asList(0, 5, 1, true, false),
            Arrays.asList(2, 5, 2, false, false), Arrays.asList(4, 5, 3, false, true)), frames);
    }

    @Test
    void streamPreprocessingFailureKeepsOwnershipWithTheCallerAndRendersNoRows() {
        AtomicInteger closed = new AtomicInteger();
        IllegalStateException failure = new IllegalStateException("cursor failed");
        try (Stream<String> stream = Stream.of("a", "b", "c").map(value -> {
            if (value.equals("c")) throw failure;
            return value;
        }).onClose(closed::incrementAndGet)) {
            StringWriter output = new StringWriter();
            List<List<Object>> frames = new ArrayList<>();
            List<List<Object>> after = new ArrayList<>();
            Map<String, Object> model = model(stream, frames, after);
            assertSame(failure, assertThrows(IllegalStateException.class, () -> template(1).merge(model, output)));
            assertEquals("", output.toString());
            assertTrue(frames.isEmpty());
            assertTrue(after.isEmpty());
            assertEquals(0, closed.get());
        }
        assertEquals(1, closed.get());
    }

    @Test
    void repeatMergesRequireFreshCursorsAndLeaveStreamClosureToTheCaller() {
        AtomicInteger closed = new AtomicInteger();
        Gene gene = template(1);
        Map<String, Object> model = model((Supplier<Object>) () -> Arrays.asList("a", "b").iterator(),
            new ArrayList<>(), new ArrayList<>());
        assertEquals("a\nb\n", gene.merge(model));
        assertEquals("a\nb\n", gene.merge(model));
        model.put("items", Arrays.asList("a", "b").iterator());
        assertEquals("a\nb\n", gene.merge(model));
        assertEquals("empty\n", gene.merge(model));
        try (Stream<String> stream = Stream.of("a", "b").onClose(closed::incrementAndGet)) {
            model.put("items", stream);
            assertEquals("a\nb\n", gene.merge(model));
            assertEquals(0, closed.get());
            assertThrows(IllegalStateException.class, () -> gene.merge(model));
            assertEquals(0, closed.get());
        }
        assertEquals(1, closed.get());
    }

    private static Gene template(int step) {
        return Parser.parse("# HI-MEEPO\n# RNA:EACH /" + step + "/items/it\n"
                            + "# RNA:RUN //capture/\n# RNA:USE /VALUE/it/*\nVALUE\n"
                            + "# RNA:ELSE it\nempty\n# RNA:DONE it\n# RNA:RUN //after/\n");
    }

    private static Map<String, Object> model(Object items, List<List<Object>> frames, List<List<Object>> after) {
        Map<String, Object> model = new HashMap<>();
        model.put("items", items);
        model.put("it", "caller");
        model.put("it._total", 99);
        model.put("it._count", 99);
        model.put("it._first", true);
        model.put("it._last", true);
        model.put("capture", (JavaEval) (ctx, previous, args) -> {
            frames.add(state(ctx));
            return null;
        });
        model.put("after", (JavaEval) (ctx, previous, args) -> {
            after.add(state(ctx));
            return null;
        });
        return model;
    }

    private static List<Object> state(Map<String, Object> context) {
        return Arrays.asList(context.get("it"), context.get("it._total"), context.get("it._count"),
            context.get("it._first"), context.get("it._last"));
    }
}
