package pro.fessional.meepo.poof.impl.map;

import org.junit.jupiter.api.Test;

import pro.fessional.meepo.poof.RnaWarmed;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class MapEngineTest {
    @Test
    void functionKeysPreservePrefixedExpressionIdentity() {
        MapEngine engine = new MapEngine();
        for (String name : new String[]{ new String("cmd"), new String("fun:cmd") }) {
            RnaWarmed plan = engine.warm("map", name + " 'argument'|" + name);
            ArrayList<RnaWarmed> steps = plan.getTypedWork();
            RnaWarmed legacy = new RnaWarmed("map", name, new Object[0], MapHelper.KIND_FUNC);
            for (RnaWarmed step : new RnaWarmed[]{ steps.get(0), steps.get(1), legacy }) {
                String alias = MapHelper.functionKey(step);
                assertEquals("fun:cmd", alias);
                assertEquals(name.startsWith("fun:"), alias == step.expr);
            }
        }
    }

    @Test
    void rawFunctionOverridesAndPrefixedFallbacksKeepTheirPrecedence() {
        MapEngine engine = new MapEngine();
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("value", "v");
        ctx.put("cmd", (Function<Object, Object>) value -> "raw");
        ctx.put("fun:cmd", (Function<Object, Object>) value -> "prefixed");
        assertEquals("raw", engine.eval(ctx, engine.warm("map", "value|cmd"), false));
        assertEquals("prefixed", engine.eval(ctx, engine.warm("map", "value|fun:cmd"), false));
        ctx.put("cmd", "not a function");
        assertEquals("prefixed", engine.eval(ctx, engine.warm("map", "value|cmd"), false));
        ctx.put("fun:cmd", null);
        assertNull(engine.eval(ctx, engine.warm("map", "value|cmd"), true));
    }

    @Test
    void legacyManuallyConstructedPlansRemainSupported() {
        MapEngine engine = new MapEngine();
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("value", "v");
        ctx.put("fun:cmd", (Function<Object, Object>) value -> "legacy");
        for (String name : new String[]{ "cmd", "fun:cmd" }) {
            ArrayList<RnaWarmed> steps = new ArrayList<>();
            steps.add(new RnaWarmed("map", "value", new String[0], MapHelper.KIND_PURE));
            steps.add(new RnaWarmed("map", name, new Object[0], MapHelper.KIND_FUNC));
            assertEquals("legacy", engine.eval(ctx, new RnaWarmed("map", "legacy", steps), false));
        }
        // A missing prefixed function exercises the legacy alias path without
        // retrying a key that already has the fun: prefix.
        ArrayList<RnaWarmed> missing = new ArrayList<>();
        missing.add(new RnaWarmed("map", "value", new String[0], MapHelper.KIND_PURE));
        missing.add(new RnaWarmed("map", "fun:missing", new Object[0], MapHelper.KIND_FUNC));
        assertThrows(IllegalStateException.class, () -> engine.eval(ctx, new RnaWarmed("map", "missing", missing), false));
    }

    @Test
    void missingBeanAttributesAndCollidingKeysRemainDistinct() {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("Aa", 1);
        ctx.put("BB", 2);
        assertEquals("Aa".hashCode(), "BB".hashCode());
        assertEquals(1, MapHelper.get(ctx, "Aa", new String[0]));
        assertEquals(2, MapHelper.get(ctx, "BB", new String[0]));
        for (int i = 0; i < 10; i++) assertNull(MapHelper.get(ctx, "missing", new String[0]));
    }

    public static class First {
        public String getValue() {
            return "first";
        }
    }

    public static class Second {
        public String value = "second";
    }

    public static class Broken {
        public String getValue() {
            throw new IllegalArgumentException("getter");
        }
    }

    private static class Hidden {
        public String getValue() {
            return "inaccessible";
        }
    }

    public static class Static {
        public static int getValue() {
            return 7;
        }
    }

    @Test
    void staticAndInaccessibleMethodsKeepTheirReflectionSemantics() {
        assertEquals(7, MapHelper.get(new Static(), "value", new String[0]));
        assertEquals("inaccessible", MapHelper.get(new Hidden(), "value", new String[0]));
        IllegalStateException failure = assertThrows(IllegalStateException.class,
            () -> MapHelper.get(Collections.singleton("x").iterator(), "hasNext", new String[0]));
        assertInstanceOf(IllegalAccessException.class, failure.getCause());
        failure = assertThrows(IllegalStateException.class, () -> MapHelper.get(new Broken(), "value", new String[0]));
        assertInstanceOf(java.lang.reflect.InvocationTargetException.class, failure.getCause());
        assertInstanceOf(IllegalArgumentException.class, failure.getCause().getCause());
    }

    @Test
    void navigationKeepsMapPrecedenceAcrossModelClasses() {
        MapEngine engine = new MapEngine();
        RnaWarmed plan = engine.warm("map", "bean.value");
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("bean", new First());
        assertEquals("first", engine.eval(ctx, plan, false));
        ctx.put("bean", new Second());
        assertEquals("second", engine.eval(ctx, plan, false));
        ctx.put("bean", Collections.singletonMap("value", "map"));
        assertEquals("map", engine.eval(ctx, plan, false));
        ctx.put("bean.value", "full-key");
        assertEquals("full-key", engine.eval(ctx, plan, false));
        ctx.put("bean.value", null);
        assertEquals("map", engine.eval(ctx, plan, false));
        ctx.put("bean", new Object());
        assertNull(engine.eval(ctx, plan, false));
        assertNull(engine.eval(ctx, plan, false));
        ctx.put("bean", new Broken());
        assertThrows(IllegalStateException.class, () -> engine.eval(ctx, plan, false));
        ctx.put("bean", Collections.singletonMap("other", "edited-plan"));
        ArrayList<RnaWarmed> pipeline = plan.getTypedWork();
        String[] parts = pipeline.get(0).getTypedWork();
        parts[1] = "other";
        assertEquals("edited-plan", engine.eval(ctx, plan, false));
        RnaWarmed missingParent = engine.warm("map", "absent.value");
        assertNull(engine.eval(ctx, missingParent, false));
    }

    @Test
    void sharedNavigationPlanSupportsConcurrentModelClasses() throws Exception {
        MapEngine engine = new MapEngine();
        RnaWarmed plan = engine.warm("map", "bean.value");
        CyclicBarrier start = new CyclicBarrier(4);
        ExecutorService workers = Executors.newFixedThreadPool(4);
        try {
            List<Future<?>> results = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                final boolean first = (i & 1) == 0;
                results.add(workers.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    Map<String, Object> ctx = Collections.singletonMap("bean", first ? new First() : new Second());
                    for (int j = 0; j < 1000; j++)
                        assertEquals(first ? "first" : "second", engine.eval(ctx, plan, false));
                    return null;
                }));
            }
            for (Future<?> result : results) result.get(30, TimeUnit.SECONDS);
        }
        finally {
            workers.shutdownNow();
        }
    }
}
