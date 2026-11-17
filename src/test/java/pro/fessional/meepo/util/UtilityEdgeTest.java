package pro.fessional.meepo.util;

import org.junit.jupiter.api.Test;
import pro.fessional.meepo.bind.wow.Clop;

import java.io.*;
import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class UtilityEdgeTest {
    public static Writer brokenWriter() {
        return new Writer() {
            @Override
            public void write(char[] chars, int off, int len) throws IOException {
                throw new IOException("fixture");
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
    }

    @Test
    void writerFailuresAreReported() {
        Writer out = brokenWriter();
        assertThrows(IllegalStateException.class, () -> Dent.write(out, new char[]{ 'x' }));
        assertThrows(IllegalStateException.class, () -> Dent.lineIt(out, new char[]{ 'x' }));
        assertThrows(IllegalStateException.class, () -> Dent.lineIt(out, "x", 0, 1));
        assertThrows(IllegalStateException.class, () -> Dent.indent(out, 1));
        assertThrows(IllegalStateException.class, () -> Dent.toString(out, new Object()));
        Dent.lineIt(out, (char[]) null);
        Dent.lineIt(out, (CharSequence) null);
        Dent.lineIt(out, null, 0, 0);
        Dent.toString(out, null);
        Dent.indent(out, 4, null);
    }

    @Test
    void renderingTypesAndIndentation() {
        Object[] values = { "s", 1, 2L, 3D, 4F, true, (short) 5, (byte) 6, 'x',
                            new BigDecimal("1E+2"), new char[]{ 'a', 'b' }, new Object() {
            public String toString() {
                return "object";
            }
        } };
        StringWriter fast = new StringWriter();
        StringBuilder slow = new StringBuilder();
        Writer writer = new Writer() {
            @Override
            public void write(char[] c, int off, int len) {
                slow.append(c, off, len);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        for (Object v : values) {
            Dent.toString(fast, v);
            Dent.toString(writer, v);
        }
        assertEquals(fast.toString(), slow.toString());
        StringWriter escaped = new StringWriter();
        Dent.lineIt(escaped, "\\\r\n\t", new Clop(0, 4, 1, 1));
        assertEquals("\\\\\\r\\n\\t", escaped.toString());
        StringWriter arrayEscaped = new StringWriter();
        Dent.lineIt(arrayEscaped, "\\\r\n\t".toCharArray());
        Dent.lineIt(arrayEscaped, new char[0]);
        assertEquals(escaped.toString(), arrayEscaped.toString());
        StringWriter indent = new StringWriter();
        Dent.indent(indent, 65);
        assertEquals(65, indent.toString().length());
        indent.getBuffer().setLength(0);
        Dent.indent(indent, 2, new Object[]{ null, "a\n", null, "b\n" });
        Dent.indent(indent, 2, Arrays.asList(null, "c\n", null, "d\n"));
        assertEquals("a\n  b\nc\n  d\n", indent.toString());
    }

    @Test
    void seekHandlesMissingTokensAndLineBoundaries() {
        assertEquals(-1, Seek.seekFollow("   ", 0, 3, "x"));
        assertEquals(-1, Seek.seekNextSplit("a1!", 0, 3));
        assertArrayEquals(new int[]{ -1, 0 }, Seek.seekPrevToken("abc", 0, 3, "x", "\\"));
        assertArrayEquals(new int[]{ -1, 0 }, Seek.seekNextToken("abc", 0, 3, "x", "\\"));
        assertEquals(-1, Seek.seekPrevGrace("\n ", 0, 2));
        assertEquals(-1, Seek.seekNextGrace(" \n", 0, 2));
        assertEquals(-1, Seek.seekPrevWhite("\nx", 0, 2));
        assertEquals(-1, Seek.seekNextWhite("x\n", 0, 2));
        assertArrayEquals(new int[]{ -1, -1 }, Seek.seekNextWords("", -1, 0));
        assertArrayEquals(new int[]{ -1, -1 }, Seek.seekNextAlnum("", 0, -1));
        for (char c : new char[]{ 'a', 'z', 'A', 'Z', '0', '9', '!', ' ', '\t', '\r', '\n', '中' }) {
            assertEquals(Character.isLetterOrDigit(c) && c < 128, Seek.isAlnum(c));
        }
    }

    @Test
    void argumentTypesAndReaderFailures() {
        assertTrue(Read.read("pom.xml", null).contains("<project"));
        assertFalse(Eval.asFalse(new Object()));
        Eval.RefStr ref = new Eval.RefStr("abc");
        assertEquals(3, ref.length());
        assertEquals('b', ref.charAt(1));
        assertEquals("bc", ref.subSequence(1, 3));
        assertEquals("x", Eval.parseArgs("'x'", Eval.ArgType.Ref).get(0).toString());
        assertEquals(Collections.singletonList("\\"), Eval.parseArgs("\\\\", Eval.ArgType.Str));
        assertThrows(IllegalStateException.class, () -> Read.read("nonexistent-meepo-resource"));
        assertThrows(IllegalStateException.class, () -> Read.read(new Reader() {
            @Override
            public int read(char[] c, int o, int n) throws IOException {
                throw new IOException("fixture");
            }

            @Override
            public void close() {
            }
        }));
        assertThrows(IllegalStateException.class, () -> Java.create(Integer.class));
        assertNotNull(Java.create(StringBuilder.class, (Object[]) null));
        assertThrows(IllegalStateException.class, () -> Java.compile("Broken", "not java"));
    }
}
