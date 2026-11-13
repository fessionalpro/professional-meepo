package pro.fessional.meepo;

import org.junit.jupiter.api.Test;
import pro.fessional.meepo.sack.Gene;
import pro.fessional.meepo.sack.Holder;
import pro.fessional.meepo.sack.Parser;

import java.io.StringWriter;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class RegressionTest {
    @Test
    void importTextInsideJavaStringsIsNotAnImportDeclaration() {
        pro.fessional.meepo.poof.impl.java.JavaEngine engine = new pro.fessional.meepo.poof.impl.java.JavaEngine();
        String code = "return \"import java.util.Date;\";";
        assertEquals("import java.util.Date;", engine.eval(Collections.emptyMap(), engine.warm("java", code), false));
    }

    @Test
    void documentedBigSnakeAliasWorks() {
        assertEquals("TRY_DO_FOR", Holder.parse("{{name | bigSnake}}").merge(Collections.singletonMap("name", "tryDoFor")));
    }

    @Test
    void optionalCaptureGroupsLeaveUnmatchedTextAlone() {
        assertEquals("x xz\n", Parser.parse("# HI-MEEPO\n# DNA:SET /x(y)?/z/\nx xy\n").merge(Collections.emptyMap()));
    }

    @Test
    void functionArgumentsContainingDotsAreNotNavigationExpressions() {
        assertEquals("  a.b", Holder.parse("{{text | fmt '%5s'}}").merge(Collections.singletonMap("text", "a.b")));
        assertEquals("2000.01.02", Holder.parse("{{now 'yyyy.MM.dd'}}").merge(Collections.singletonMap("fun:now",
            (pro.fessional.meepo.eval.JavaEval) (ctx, obj, args) -> pro.fessional.meepo.eval.time.Now.funNow.eval(ctx,
                java.time.LocalDate.of(2000, 1, 2), args))));
    }

    @Test
    void quotedWhitespaceAndEscapedSeparatorsArePreserved() {
        assertEquals(Collections.singletonList(" a "), pro.fessional.meepo.util.Eval.parseArgs("\" a \"", pro.fessional.meepo.util.Eval.ArgType.Str));
        assertEquals(Arrays.asList("a|", "b"), pro.fessional.meepo.util.Eval.split("a\\||b", '|', '\\'));
        assertArrayEquals(new int[]{ 3, 3 }, pro.fessional.meepo.util.Seek.trimBlank(" \t\n", 0, 3));
        assertEquals(2, pro.fessional.meepo.util.Seek.seekToken("###", 0, 3, "#", true));
    }

    @Test
    void nestedRelativeIncludesUseTheContainingTemplate(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        java.nio.file.Path sub = java.nio.file.Files.createDirectories(dir.resolve("with space/sub"));
        java.nio.file.Path parent = sub.getParent().resolve("parent.txt");
        java.nio.file.Files.write(parent, "# HI-MEEPO\n# DNA:SON ./sub/child.txt\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        java.nio.file.Files.write(sub.resolve("child.txt"), "# HI-MEEPO\n# DNA:SON ./leaf.txt\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        java.nio.file.Files.write(sub.resolve("leaf.txt"), "leaf".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals("leaf", Meepo.parse(parent.toFile(), 0).merge(Collections.emptyMap()));
        assertEquals("leaf", Meepo.parse(parent.toString(), 0).merge(Collections.emptyMap()));
        java.nio.file.Files.write(sub.resolve("leaf.txt"), "# HI-MEEPO\n# DNA:SON ../parent.txt\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThrows(IllegalStateException.class, () -> Meepo.parse(parent.toFile(), 0));
    }

    @Test
    void neverCacheDoesNotStoreOrReuseEntries() {
        String key = UUID.randomUUID().toString();
        Gene first = Meepo.cache(key, () -> Parser.parse("first"), -1);
        Gene second = Meepo.cache(key, () -> Parser.parse("second"), 0);
        assertNotSame(first, second);
        assertEquals("second", second.merge(Collections.emptyMap()));
        assertSame(first, Meepo.cache(key, () -> Parser.parse("third"), -1));
    }

    @Test
    void geneTakesImmutableSnapshots() {
        List<pro.fessional.meepo.bind.Exon> nodes = new ArrayList<>();
        Set<String> engines = new HashSet<>();
        Gene gene = new Gene(nodes, engines);
        engines.add("raw");
        nodes.add(new pro.fessional.meepo.bind.txt.TxtSimple("x", new pro.fessional.meepo.bind.wow.Clop(0, 1, 1, 1)));
        assertTrue(gene.exon.isEmpty());
        assertTrue(gene.rngs.isEmpty());
    }

    @Test
    void numericAndVisibilityFunctionsPreserveValues() {
        Map<String, Object> ctx = Collections.emptyMap();
        assertEquals(1L, pro.fessional.meepo.eval.num.Cal.funAbs.eval(ctx, true));
        assertEquals(0L, pro.fessional.meepo.eval.num.Cal.funAbs.eval(ctx, false));
        assertEquals(new java.math.BigDecimal("9223372036854775808"),
            pro.fessional.meepo.eval.num.Cal.funAbs.eval(ctx, Long.MIN_VALUE));
        assertEquals("odd", pro.fessional.meepo.eval.num.Cal.funMod.eval(ctx, -1, "even", "odd"));
        assertEquals(7, pro.fessional.meepo.eval.ctrl.Elv.funSee.eval(ctx, null, 7));
    }

    @Test
    void dynamicJavaMayDeclareAnonymousClasses() throws Exception {
        Class<?> type = pro.fessional.meepo.util.Java.compile("MultiOutput",
            "public class MultiOutput { public String value() { return new Object() {"
            + "public String toString() { return \"ok\"; } }.toString(); } }");
        assertEquals("ok", type.getMethod("value").invoke(type.newInstance()));
    }

    @Test
    void executionOnlyRunExecutesExactlyOnceWithoutFollowingText() {
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        Map<String, Object> ctx = Collections.singletonMap("counter", (java.util.function.Supplier<Integer>) calls::incrementAndGet);
        Gene gene = Parser.parse("# HI-MEEPO\n# RNA:RUN //counter/\n");
        assertEquals("", gene.merge(ctx));
        assertEquals(1, calls.get());
        assertEquals("", gene.merge(ctx));
        assertEquals(2, calls.get());
    }

    @Test
    void defaultPieceUsesPlaceholderParser() {
        assertEquals("Hello Meepo", Meepo.piece(Collections.singletonMap("name", "Meepo"), "Hello {{name}}"));
    }

    @Test
    void escapeTokensMayContainMultipleCharacters() {
        Map<String, Object> ctx = Collections.singletonMap("name", "Meepo");
        assertEquals("{{name}}", Holder.parse("##{{name##}}", "{{", "}}", "##").merge(ctx));
        assertEquals("##Meepo", Holder.parse("####{{name}}", "{{", "}}", "##").merge(ctx));
    }

    @Test
    void staticMergeRetainsItsResult() {
        Gene gene = Parser.parse("static template");
        String first = gene.merge(Collections.emptyMap());
        assertSame(first, gene.merge(Collections.emptyMap()));
        StringWriter writer = new StringWriter();
        gene.merge(Collections.emptyMap(), writer);
        assertEquals(first, writer.toString());
    }

    @Test
    void allCollectionsHonorStepAndMetadata() {
        List<Integer> values = Arrays.asList(0, 1, 2, 3, 4);
        List<Object> sources = Arrays.asList(values, new LinkedList<>(values), new ArrayDeque<>(values),
            new LinkedHashSet<>(values), values.toArray(), new int[]{ 0, 1, 2, 3, 4 });
        for (Object source : sources) {
            for (int step : new int[]{ 1, 2, 3, -1, -2, -3, Integer.MAX_VALUE, Integer.MIN_VALUE }) {
                String template = "# HI-MEEPO\n# RNA:EACH /" + step + "/items/it\n"
                                  + "# RNA:USE /VALUE/it/*\n# RNA:USE /COUNT/it._count/*\n"
                                  + "# RNA:USE /FIRST/it._first/*\n# RNA:USE /LAST/it._last/*\n"
                                  + "VALUE:COUNT:FIRST:LAST\n# RNA:DONE it\n";
                StringBuilder expected = new StringBuilder();
                long stride = Math.abs((long) step);
                int count = 0;
                for (long index = step > 0 ? 0 : 4; index >= 0 && index < 5; index += step) {
                    count++;
                    expected.append(index).append(':').append(count).append(':').append(count == 1)
                            .append(':').append(count == (4 / stride + 1)).append('\n');
                }
                assertEquals(expected.toString(), Parser.parse(template).merge(Collections.singletonMap("items", source)),
                    source.getClass() + " step=" + step);
            }
        }
    }

    @Test
    void emptyLoopsResetCounters() {
        String template = "# HI-MEEPO\n# RNA:EACH /1/items/it\nunused\n# RNA:ELSE it\nempty\n"
                          + "# RNA:DONE it\n# RNA:USE /TOTAL/it._total/\n# RNA:USE /COUNT/it._count/\nTOTAL:COUNT\n";
        assertEquals("empty\n0:0\n", Parser.parse(template).merge(Collections.singletonMap("items", Collections.emptyList())));
    }

    @Test
    void whenRequiresACompleteControlWord() {
        for (String word : Arrays.asList("e", "es", "o", "ot", "y|yes")) {
            assertThrows(IllegalStateException.class, () -> Parser.parse("# HI-MEEPO\n# RNA:WHEN /" + word
                                                                         + "/flag/bg\ntext\n# RNA:DONE bg\n"), word);
        }
    }
}
