package pro.fessional.meepo.pack;

import org.junit.jupiter.api.Test;
import pro.fessional.meepo.util.Dent;

import java.io.StringWriter;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class CbufTest {
    @Test
    void writerOperationsMatchStringWriterAndGrowSafely() throws Exception {
        Cbuf actual = new Cbuf(0);
        StringWriter expected = new StringWriter(0);
        for (java.io.Writer out : new java.io.Writer[]{ actual, expected }) {
            out.write(new char[]{ 'a', 'b' });
            out.write(new char[]{ 'x', 'c', 'y' }, 1, 1);
            out.write("中文🙂");
            out.write("0d1", 1, 1);
            out.write(0x10065);
            out.append((CharSequence) null).append("null", 1, 3).append('!');
            out.append("<").append(new StringBuilder("mutable")).append(">");
            out.write((String) null);
            out.flush();
            out.close();
            out.write("after close");
        }
        assertEquals(expected.toString(), actual.toString());
        String snapshot = actual.toString();
        actual.write("changed");
        assertEquals(expected.toString(), snapshot);
        assertThrows(IllegalArgumentException.class, () -> new Cbuf(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> actual.write(new char[0], -1, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> actual.append("x", 0, 2));
    }

    @Test
    void objectFormattingMatchesTheExistingBufferIncludingNumericExtremes() {
        Cbuf actual = new Cbuf(1);
        StringWriter expected = new StringWriter();
        Object[] values = { "text", new StringBuilder("sequence"), Integer.MIN_VALUE, Long.MIN_VALUE,
                            -0D, Double.NaN, Double.POSITIVE_INFINITY, Float.MIN_VALUE, true, false, (short) -12,
                            (byte) -8, '中', new BigDecimal("1E+30"), new char[]{ 'x', 'y' },
                            new Object() {
                                @Override
                                public String toString() {
                                    return "object";
                                }
                            }, null };
        for (Object value : values) {
            Dent.toString(actual, value);
            Dent.toString(expected, value);
        }
        assertEquals(expected.toString(), actual.toString());
    }
}
