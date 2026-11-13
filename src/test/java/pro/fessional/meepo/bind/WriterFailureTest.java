package pro.fessional.meepo.bind;

import org.junit.jupiter.api.Test;
import pro.fessional.meepo.bind.dna.*;
import pro.fessional.meepo.bind.rna.*;
import pro.fessional.meepo.bind.txt.*;
import pro.fessional.meepo.bind.wow.*;
import pro.fessional.meepo.sack.*;
import pro.fessional.meepo.util.UtilityEdgeTest;

import java.io.*;
import java.lang.reflect.*;
import java.lang.ref.SoftReference;
import java.util.*;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class WriterFailureTest {
    @Test
    void diagnosticWritersReportIoFailures() throws Exception {
        Clop edge = new Clop(0, 1, 1, 1);
        Life life = Life.nobodyAny();
        Pattern find = Pattern.compile("x");
        List<Object> objects = Arrays.asList(edge, life,
            new DnaBkb("x", edge, "b"), new DnaEnd("x", edge, Collections.singleton("b")),
            new DnaRaw("x", edge, 0, 1), new DnaSet("x", edge, life, find, "y"), new DnaSon("x", edge, "path"),
            new RnaUse("x", edge, life, find, "key"), new RnaRun("x", edge, life, "raw", find, "y", false),
            new RnaPut("x", edge, "raw", "key", "y", false), new RnaWhen("x", edge, "bg", "map", false, "flag", false),
            new RnaEach("x", edge, "it", "map", 1, "items", false), new RnaElse("x", edge, "bg"),
            new RnaDone("x", edge, Collections.singleton("bg")), new HiMeepo("x", edge, "#", "\n", true),
            new TxtSimple("x", edge), new TxtDnaSet("x", edge, "y"),
            new TxtRnaUse("x", edge, "key", 0), new TxtRnaRun("x", edge, "raw", "y", false, 0));
        for (Object object : objects) {
            Method method = object.getClass().getMethod("toString", Writer.class);
            InvocationTargetException e = assertThrows(InvocationTargetException.class,
                () -> method.invoke(object, UtilityEdgeTest.brokenWriter()), object.getClass().getName());
            assertInstanceOf(IllegalStateException.class, e.getCause());
            StringWriter writer = new StringWriter();
            method.invoke(object, writer);
            assertFalse(writer.toString().isEmpty());
        }
        assertThrows(IllegalStateException.class, () -> Gene.graph(UtilityEdgeTest.brokenWriter(),
            Collections.singletonList(new TxtSimple("x", edge)), 0, 0));
    }

    @Test
    void clearedSoftCacheIsRebuilt() throws Exception {
        Gene gene = Parser.parse("hello");
        gene.merge(Collections.emptyMap());
        Field field = Gene.class.getDeclaredField("text9");
        field.setAccessible(true);
        ((SoftReference<?>) field.get(gene)).clear();
        assertEquals("hello", gene.merge(Collections.emptyMap()));
    }

    @Test
    void rangesAndRegexCaptures() {
        assertTrue(new Clop(1, 2, 1, 1).compareTo(new Clop(2, 3, 1, 1)) < 0);
        assertTrue(new Clop(1, 3, 1, 1).compareTo(new Clop(1, 2, 1, 1)) < 0);
        DnaSet empty = new DnaSet("", new Clop(0, 0, 1, 1), Life.nobodyOne(), Pattern.compile("x"), "");
        assertTrue(empty.apply(new Clop(0, 0, 1, 1), "", 0).isEmpty());
        assertEquals("<y>\n", Parser.parse("# HI-MEEPO\n# DNA:SET /<(x)>/y/\n<x>\n").merge(Collections.emptyMap()));
        Life merged = Life.parse("1-1,5-3,4-8");
        for (int i = 1; i <= 8; i++) {
            assertEquals(i == 2 ? Life.State.Skip : Life.State.Live, merged.enjoy());
        }
        assertEquals(Life.State.Dead, merged.enjoy());
    }
}
