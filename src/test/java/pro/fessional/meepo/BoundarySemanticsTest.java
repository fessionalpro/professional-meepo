package pro.fessional.meepo;

import org.junit.jupiter.api.Test;
import pro.fessional.meepo.eval.ctrl.Elv;
import pro.fessional.meepo.eval.fmt.Case;
import pro.fessional.meepo.eval.num.Cal;
import pro.fessional.meepo.eval.time.Now;
import pro.fessional.meepo.sack.Parser;
import pro.fessional.meepo.util.*;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class BoundarySemanticsTest {
    @Test
    void truthinessAndOptionalArguments() {
        assertTrue(Now.envNowDate.get().matches("\\d{4}-\\d{2}-\\d{2}"));
        assertTrue(Now.envNowTime.get().matches("\\d{2}:\\d{2}:\\d{2}"));
        assertEquals("classpath fixture\n", Read.read("plain.txt"));
        assertFalse(Eval.asFalse("text"));
        assertFalse(Eval.asFalse(-1D));
        assertFalse(Eval.asFalse(1D));
        assertFalse(Eval.asFalse(new int[]{ 1 }));
        assertTrue(Eval.parseArgs(null, Eval.ArgType.Obj).isEmpty());
        assertTrue(Eval.parseArgs("", Eval.ArgType.Obj).isEmpty());
        assertEquals(Arrays.asList("a", "b", "c"), Eval.parseArgs("a\tb\rc\n", Eval.ArgType.Str));
        Map<String, Object> ctx = Collections.emptyMap();
        for (Number value : Arrays.asList((byte) -2, (short) -2, -2, -2L)) {
            assertEquals(2L, Cal.funAbs.eval(ctx, value));
        }
        assertThrows(IllegalArgumentException.class, () -> Cal.funMod.eval(ctx, 1));
        assertEquals(1, Elv.funSee.eval(ctx, 1));
        LocalDateTime time = LocalDateTime.of(2000, 1, 2, 3, 4, 5);
        assertEquals("2000-01-02 03:04:05", Now.funNow.eval(ctx, time, (Object[]) null));
        assertEquals("2000-01-02 03:04:05", Now.funNow.eval(ctx, time, new Object[]{ null }));
        assertEquals("a_b", Case.funSnakeCase.eval(ctx, "a B", (Object[]) null));
        assertEquals("a_b", Case.funSnakeCase.eval(ctx, "a B", new Object[]{ null }));
        assertEquals("abc", Case.wordCase("!abc", Case.Type.Lower, '_'));
        assertEquals("abc", Case.wordCase("!abc", false));
        assertEquals("x", Read.read(new ByteArrayInputStream(new byte[]{ 'x' }), null));
    }

    @Test
    void invalidSeekRangesAndWhitespace() {
        for (int[] range : Arrays.asList(new int[]{ -1, 0 }, new int[]{ 0, -1 })) {
            assertEquals(-1, Seek.seekPrevGrace("", range[0], range[1]));
            assertEquals(-1, Seek.seekNextGrace("", range[0], range[1]));
            assertEquals(-1, Seek.seekPrevWhite("", range[0], range[1]));
            assertEquals(-1, Seek.seekNextWhite("", range[0], range[1]));
            assertArrayEquals(new int[]{ -1, -1 }, Seek.seekPrevWords("", range[0], range[1]));
            assertArrayEquals(new int[]{ -1, -1 }, Seek.seekNextWords("", range[0], range[1]));
            assertArrayEquals(new int[]{ -1, -1 }, Seek.seekNextAlnum("", range[0], range[1]));
        }
        for (char c : new char[]{ ' ', '\t', '\r', '\n', 'x' }) {
            assertEquals(c == ' ' || c == '\t' || c == '\r', Seek.isWhite(c));
            assertEquals(!Seek.isWhite(c), Seek.notWhite(c));
            assertEquals(c == 'x', Seek.notBlank(c));
        }
        assertEquals(0, Seek.countPrevToken("x", 0, null));
        assertNull(Parser.parse(null));
    }

    @Test
    void staticTemplateAndGraphAreSafeToShare() throws Exception {
        pro.fessional.meepo.sack.Gene gene = Parser.parse("shared static text");
        ExecutorService workers = Executors.newFixedThreadPool(4);
        try {
            List<Callable<String>> jobs = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                jobs.add(() -> {
                    assertTrue(gene.graph().contains("TxtSimple"));
                    return gene.merge(Collections.emptyMap());
                });
            }
            for (Future<String> result : workers.invokeAll(jobs)) assertEquals("shared static text", result.get());
        }
        finally {
            workers.shutdownNow();
        }
    }
}
