package pro.fessional.meepo.poof;

import org.junit.jupiter.api.Test;
import pro.fessional.meepo.eval.*;
import pro.fessional.meepo.eval.ctrl.Elv;
import pro.fessional.meepo.eval.fmt.*;
import pro.fessional.meepo.eval.num.Cal;
import pro.fessional.meepo.eval.time.Now;
import pro.fessional.meepo.poof.impl.*;
import pro.fessional.meepo.poof.impl.java.JavaEngine;
import pro.fessional.meepo.poof.impl.map.*;
import pro.fessional.meepo.sack.Holder;

import java.util.*;
import java.util.function.*;

import static org.junit.jupiter.api.Assertions.*;

class EngineEdgeTest {
    @Test
    void mapSuppliersFunctionsAndFailures() {
        MapEngine engine = new MapEngine();
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("supplier", (Supplier<String>) () -> "supplied");
        ctx.put("function", (Function<Object, Object>) arg -> arg == null ? "no-arg" : "args");
        ctx.put("value", "v");
        assertEquals("supplied", engine.eval(ctx, engine.warm("map", "supplier"), false));
        assertEquals("no-arg", engine.eval(ctx, engine.warm("map", "function"), false));
        assertEquals("args", engine.eval(ctx, engine.warm("map", "function a"), false));
        assertThrows(IllegalStateException.class, () -> engine.eval(ctx, engine.warm("map", "value | missing"), false));
        engine.eval(ctx, engine.warm("map", "value | missing"), true);
        ctx.put("supplier", (Supplier<Object>) () -> {
            throw new IllegalArgumentException("fixture");
        });
        assertThrows(IllegalStateException.class, () -> engine.eval(ctx, engine.warm("map", "supplier"), false));
        assertNull(engine.eval(ctx, engine.warm("map", "supplier"), true));
        ctx.put("bean", new ThrowingBean());
        assertThrows(IllegalStateException.class, () -> MapHelper.get(ctx, "bean.value", new String[]{ "bean", "value" }));
    }

    public static class ThrowingBean {
        public String getValue() {
            throw new IllegalArgumentException("fixture");
        }
    }

    @Test
    void scriptAndJavaExecutionFailures() {
        Map<String, Object> ctx = new HashMap<>();
        JsEngine js = new JsEngine();
        RnaWarmed badJs = js.warm("js", "throw new Error('fixture')");
        assertThrows(IllegalStateException.class, () -> js.eval(ctx, badJs, false));
        assertNull(js.eval(ctx, badJs, true));
        JavaEngine java = new JavaEngine();
        RnaWarmed badJava = java.warm("java", "throw new IllegalArgumentException(\"fixture\");");
        assertThrows(IllegalStateException.class, () -> java.eval(ctx, badJava, false));
        assertNull(java.eval(ctx, badJava, true));
        assertThrows(IllegalStateException.class, () -> java.warm("java", "invalid java source"));
        UriEngine uri = new UriEngine();
        assertNull(uri.eval(ctx, uri.warm("uri", "classpath:/missing-fixture"), true));
        assertThrows(IllegalStateException.class, () -> uri.eval(ctx, uri.warm("uri", "classpath:/missing-fixture"), false));
        OsEngine os = new OsEngine();
        assertTrue(os.warm("unsupported", "echo").hasInfo());
        assertSame(os, os.fork());
        RnaWarmed badOs = os.warm("exe", "missing-meepo-command-fixture");
        assertThrows(IllegalStateException.class, () -> os.eval(ctx, badOs, false));
        assertEquals("", os.eval(ctx, badOs, true));
        os.eval(ctx, os.warm("cmd", "echo fixture"), true);
    }

    @Test
    void registryReplacementAndWarmupDiagnostics() {
        RnaEngine original = RnaManager.getDefault();
        MapEngine engine = new MapEngine();
        try {
            RnaManager.setDefault(engine);
            RnaManager.register(engine);
            RnaManager.register((RnaEngine) null);
            assertTrue(RnaManager.getEngineCount() > 0);
            assertSame(engine, RnaManager.getDefault());
            StringBuilder errors = new StringBuilder();
            RngChecker checker = new RngChecker();
            RnaWarmed first = checker.check(errors, "unknown-fixture", "key");
            assertSame(first, checker.check(errors, "unknown-fixture", "key"));
            assertTrue(errors.toString().contains("dis-match"));
            RnaEngine bad = new RnaEngine() {
                public String[] type() {
                    return new String[]{ "bad-fixture" };
                }

                public RnaWarmed warm(String type, String expr) {
                    RnaWarmed w = new RnaWarmed(type, expr);
                    w.info = "bad fixture";
                    return w;
                }

                public Object eval(Map<String, Object> ctx, RnaWarmed expr, boolean mute) {
                    return null;
                }

                public RnaEngine fork() {
                    return this;
                }
            };
            RnaManager.register(bad);
            assertTrue(new RngChecker().check(new StringBuilder(), "bad-fixture", "x").hasInfo());
            RnaManager.setDefault(new MapEngine() {
                @Override
                public RnaWarmed warm(String type, String expr) {
                    RnaWarmed warmed = super.warm(type, expr);
                    warmed.info = "fixture validation failure";
                    return warmed;
                }
            });
            assertThrows(IllegalStateException.class, () -> Holder.parse("{{key}}"));
            assertNotNull(Holder.parse(true, "{{key}}"));
        }
        finally {
            RnaManager.setDefault(original);
        }
        Function<Object, Object> identity = Function.identity();
        RnaManager.register("fixture-function", identity);
        RnaManager.register("fixture-function", identity);
        RnaManager.register("fixture-function", (JavaEval) (ctx, obj, args) -> obj);
        assertNotNull(RnaManager.getFunction("fixture-function"));
    }

    @Test
    void builtInFunctionEdges() {
        Map<String, Object> ctx = Collections.emptyMap();
        assertEquals("even", Cal.funMod.eval(ctx, null, "even", "odd"));
        assertEquals("odd", Cal.funMod.eval(ctx, true, "even", "odd"));
        assertEquals("even", Cal.funMod.eval(ctx, false, "even", "odd"));
        assertEquals(2, Cal.funMod.eval(ctx, "3", 1, 2));
        assertEquals(0L, Cal.funAbs.eval(ctx, null));
        assertNull(Elv.funSee.eval(ctx, null, "", new pro.fessional.meepo.util.Eval.RefStr("missing")));
        assertEquals("İ", Case.funUpperCase.eval(ctx, "i", "tr"));
        assertEquals("ı", Case.funLowerCase.eval(ctx, "I", "tr"));
        assertEquals("a_b", Case.wordCase("a_b", Case.Type.Lower, '_'));
        assertEquals("aB", Case.wordCase("a_b", false));
        for (NameEval fun : Arrays.asList(Case.funCamelCase, Case.funPascalCase, Case.funSnakeCase,
            Case.funBigSnake, Case.funKebabCase, Case.funBigKebab, Case.funDotCase, Case.funUpperCase, Case.funLowerCase)) {
            assertEquals("", fun.eval(ctx, null));
        }
    }
}
