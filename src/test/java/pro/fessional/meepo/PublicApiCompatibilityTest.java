package pro.fessional.meepo;

import org.junit.jupiter.api.Test;
import pro.fessional.meepo.bind.wow.*;
import pro.fessional.meepo.bind.Exon;
import pro.fessional.meepo.bind.rna.RnaDone;
import pro.fessional.meepo.pack.Utxt;
import pro.fessional.meepo.eval.*;
import pro.fessional.meepo.eval.ctrl.Elv;
import pro.fessional.meepo.eval.fmt.*;
import pro.fessional.meepo.eval.num.Cal;
import pro.fessional.meepo.eval.time.Now;
import pro.fessional.meepo.poof.RnaManager;
import pro.fessional.meepo.poof.impl.map.MapHelper;
import pro.fessional.meepo.sack.*;
import pro.fessional.meepo.util.*;

import java.io.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class PublicApiCompatibilityTest {
    @Test
    void geneKeepsOriginalMergeSignaturesWithoutByteConvenienceEntrypoints() throws Exception {
        assertEquals(String.class, Gene.class.getMethod("merge", Map.class).getReturnType());
        assertEquals(String.class, Gene.class.getMethod("merge", Map.class, int.class).getReturnType());
        assertEquals(void.class, Gene.class.getMethod("merge", Map.class, Writer.class).getReturnType());
        assertThrows(NoSuchMethodException.class,
            () -> Gene.class.getMethod("merge", Map.class, Writer.class, java.nio.charset.Charset.class));
        assertThrows(NoSuchMethodException.class,
            () -> pro.fessional.meepo.bind.Exon.class.getMethod("merge", Acid.class, Writer.class, java.nio.charset.Charset.class));
        assertEquals(String.class, Gene.class.getMethod("build").getReturnType());
        assertEquals(String.class, Gene.class.getMethod("graph").getReturnType());
        assertNotNull(Gene.class.getConstructor(List.class, Set.class));
        Set<String> originalNames = new HashSet<>(Arrays.asList("merge", "build", "graph"));
        for (java.lang.reflect.Method method : Gene.class.getDeclaredMethods()) {
            if (!java.lang.reflect.Modifier.isPublic(method.getModifiers())) continue;
            assertTrue(originalNames.contains(method.getName()), method.toString());
            assertNotEquals(byte[].class, method.getReturnType());
            assertNotEquals(InputStream.class, method.getReturnType());
        }
    }

    @Test
    void existingUtilityClassesRemainConstructible() throws Exception {
        for (Class<?> type : Arrays.asList(Meepo.class, FunEnv.class, Cal.class, Elv.class, Case.class,
            Fmt.class, Now.class, Eval.class, Dent.class, Read.class, Java.class, Seek.class,
            Holder.class, Parser.class, RnaManager.class, MapHelper.class)) {
            assertNotNull(type.newInstance());
        }
        class Extension extends Tick {
            Extension() {
                super(new Utxt(new char[0]), new Clop(0, 0, 1, 1), Life.nobodyAny());
            }
        }
        assertNotNull(new Extension().life);
    }

    @Test
    void copiedControlNodesShareFixedTextThroughUtxtConstructors() throws Exception {
        String source = "\\\r\n\t中文😀";
        Clop span = new Clop(0, source.length(), 1, 1);
        Utxt fixed = new Utxt(source.toCharArray());
        class Fragment extends Exon {
            Fragment() {
                super(fixed, span);
            }
        }
        class Control extends Tock {
            Control() {
                super(fixed, span, "group");
            }
        }
        class Done extends RnaDone {
            Done() {
                super(fixed, span, "group", Collections.singleton("name"));
            }
        }
        Exon control = new Control();
        Exon fragment = new Fragment();
        RnaDone done = new Done();
        RnaDone copy = done.copy("other");
        java.lang.reflect.Field text = Exon.class.getDeclaredField("text9");
        text.setAccessible(true);
        assertEquals(Utxt.class, text.getType());
        assertSame(fixed, text.get(fragment));
        assertSame(fixed, text.get(control));
        assertSame(fixed, text.get(done));
        assertSame(text.get(done), text.get(copy));
        for (Exon node : Arrays.asList(fragment, control, done, copy)) {
            StringWriter out = new StringWriter();
            node.build(out);
            assertEquals(source, out.toString());
        }
        assertTrue(copy.toString().contains("tock='other'"));
        assertEquals("group", done.tock);
        assertEquals("other", copy.tock);
        assertThrows(NoSuchMethodException.class, () -> Exon.class.getDeclaredConstructor(char[].class, Clop.class));
        assertThrows(NoSuchMethodException.class, () -> Tock.class.getDeclaredConstructor(char[].class, Clop.class, String.class));
        assertThrows(NoSuchMethodException.class, () -> RnaDone.class.getDeclaredConstructor(char[].class, Clop.class, String.class, Collection.class));
        assertThrows(NoSuchMethodException.class, () -> Tick.class.getDeclaredConstructor(char[].class, Clop.class, Life.class));
    }

    @Test
    void graphReportsWriterFailureAfterTreePrefix() {
        Writer out = new Writer() {
            boolean first = true;

            @Override
            public void write(char[] c, int off, int len) throws IOException {
                if (first) first = false;
                else throw new IOException("fixture");
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        assertThrows(IllegalStateException.class, () -> Gene.graph(out, Parser.parse("x").exon, 1, 0));
    }

    @Test
    void runnableJarEntryPoint() throws Exception {
        PrintStream original = System.out;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(bytes, true, "UTF-8")) {
            System.setOut(out);
            assertNotNull(new pro.fessional.WhoAmI());
            pro.fessional.WhoAmI.main(new String[0]);
        }
        finally {
            System.setOut(original);
        }
        assertTrue(bytes.toString("UTF-8").contains("Meepo:"));
    }
}
