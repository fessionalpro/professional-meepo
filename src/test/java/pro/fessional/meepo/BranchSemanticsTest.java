package pro.fessional.meepo;

import org.junit.jupiter.api.Test;
import pro.fessional.meepo.bind.txt.TxtRnaRun;
import pro.fessional.meepo.bind.wow.Clop;
import pro.fessional.meepo.poof.RnaWarmed;
import pro.fessional.meepo.poof.RngChecker;
import pro.fessional.meepo.poof.impl.java.JavaEngine;
import pro.fessional.meepo.poof.impl.map.MapEngine;
import pro.fessional.meepo.poof.impl.map.MapHelper;
import pro.fessional.meepo.sack.Acid;
import pro.fessional.meepo.sack.Gene;
import pro.fessional.meepo.sack.Parser;
import pro.fessional.meepo.util.Java;

import java.io.StringWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

class BranchSemanticsTest {
    @Test
    void staticUtf8CanBeTheFirstOutputWithoutPrewarmingAStringCache() {
        Gene gene = Parser.parse("首次输出😀");
        assertArrayEquals("首次输出😀".getBytes(UTF_8), TestingHelper.bytes(gene, Collections.emptyMap()));
        assertArrayEquals("首次输出😀".getBytes(UTF_8), TestingHelper.bytes(gene, Collections.emptyMap()));
    }

    @Test
    void javaHandlesEmptyPlansEmptyBodiesAndUnknownGeneratedClasses() {
        JavaEngine engine = new JavaEngine();
        assertNull(engine.eval(Collections.emptyMap(), RnaWarmed.EMPTY, false));
        assertThrows(IllegalStateException.class, () -> engine.warm("java", " \t\r\n"));
        Class<?> generated = Java.compile("BranchGenerated", "public class BranchGenerated {}\n");
        assertThrows(ClassNotFoundException.class, () -> generated.getClassLoader().loadClass("BranchMissing"));
    }

    @Test
    void emptyRunEdgesExecuteSideEffectsWithoutWritingTheirValue() {
        AtomicInteger calls = new AtomicInteger();
        Map<String, Object> context = Collections.singletonMap("effect", (Supplier<Integer>) calls::incrementAndGet);
        TxtRnaRun run = new TxtRnaRun("", new Clop(0, 0, 1, 1), "map", "effect", false, 0);
        RngChecker checker = new RngChecker();
        StringBuilder errors = new StringBuilder();
        run.check(errors, checker);
        assertEquals(0, errors.length());
        Acid acid = new Acid(context, checker.getCheckedEngine());
        try {
            StringWriter out = new StringWriter();
            run.merge(acid, out);
            assertEquals(1, calls.get());
            assertEquals("", out.toString());
        }
        finally {
            acid.clear();
        }
    }

    @Test
    void negativeConditionAliasesHaveIdenticalOutput() {
        for (String alias : new String[]{ "n", "no", "not" }) {
            Gene gene = Parser.parse("# HI-MEEPO\n# RNA:WHEN /" + alias + "/flag/choice\nnegative\n"
                                     + "# RNA:ELSE choice\npositive\n# RNA:DONE choice\n");
            assertEquals("negative\n", gene.merge(Collections.singletonMap("flag", false)), alias);
            assertEquals("positive\n", gene.merge(Collections.singletonMap("flag", true)), alias);
        }
    }

    public static class Attributes {
        public int unrelated = 1;
        public int wanted = 2;

        public boolean hasReady() {
            return true;
        }

        public String getAbc() {
            return "abc";
        }
    }

    @Test
    void hasGettersFieldsAndAlmostMatchingNamesRemainDistinct() {
        Attributes bean = new Attributes();
        assertEquals(true, MapHelper.get(bean, "ready", new String[0]));
        assertEquals(1, MapHelper.get(bean, "unrelated", new String[0]));
        assertEquals(2, MapHelper.get(bean, "wanted", new String[0]));
        assertNull(MapHelper.get(bean, "abd", new String[0]));
    }

    @Test
    void prefixedInvalidFunctionsDoNotRetryTheIdenticalAlias() {
        MapEngine engine = new MapEngine();
        Map<String, Object> context = new HashMap<>();
        context.put("fun:scalar", 17);
        assertEquals(17, engine.eval(context, engine.warm("map", "fun:scalar 1"), false));
        context.put("value", "v");
        assertThrows(IllegalStateException.class, () -> engine.eval(context, engine.warm("map", "value|fun:missing"), false));
    }
}
