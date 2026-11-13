package pro.fessional.meepo.pack;

import java.io.Writer;
import java.math.BigDecimal;

/**
 * Cbuf (character buffer): a Writer backed by a thread-confined StringBuilder.
 * <p>Collects one render's characters using the running JDK's public bulk-copy
 * implementation. flush and close are no-ops, like StringWriter; toString returns
 * a stable String snapshot. Instances must not be shared between render threads.</p>
 */
public final class Cbuf extends Writer {
    private final StringBuilder buffer;

    /**
     * @param capacity nonnegative initial character capacity; the buffer grows as needed
     */
    public Cbuf(int capacity) {
        if (capacity < 0) throw new IllegalArgumentException("negative buffer size");
        buffer = new StringBuilder(capacity);
    }

    @Override
    public void write(char[] value) {
        buffer.append(value);
    }

    @Override
    public void write(char[] value, int offset, int length) {
        buffer.append(value, offset, length);
    }

    @Override
    public void write(String value) {
        buffer.append(value);
    }

    @Override
    public void write(String value, int offset, int length) {
        buffer.append(value, offset, offset + length);
    }

    @Override
    public void write(int value) {
        buffer.append((char) value);
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
    }

    @Override
    public Cbuf append(CharSequence value) {
        buffer.append(value);
        return this;
    }

    @Override
    public Cbuf append(CharSequence value, int start, int end) {
        buffer.append(value, start, end);
        return this;
    }

    @Override
    public Cbuf append(char value) {
        buffer.append(value);
        return this;
    }

    /**
     * Append a render value, preserving plain BigDecimal and char-array formatting.
     */
    public void writeValue(Object value) {
        if (value instanceof CharSequence) buffer.append((CharSequence) value);
        else if (value instanceof Integer) buffer.append(((Integer) value).intValue());
        else if (value instanceof Long) buffer.append(((Long) value).longValue());
        else if (value instanceof Double) buffer.append(((Double) value).doubleValue());
        else if (value instanceof Float) buffer.append(((Float) value).floatValue());
        else if (value instanceof Boolean) buffer.append(((Boolean) value).booleanValue());
        else if (value instanceof Short) buffer.append(((Short) value).shortValue());
        else if (value instanceof Byte) buffer.append(((Byte) value).byteValue());
        else if (value instanceof Character) buffer.append(((Character) value).charValue());
        else if (value instanceof BigDecimal) buffer.append(((BigDecimal) value).toPlainString());
        else if (value instanceof char[]) buffer.append((char[]) value);
        else buffer.append(value);
    }

    @Override
    public String toString() {
        return buffer.toString();
    }
}
