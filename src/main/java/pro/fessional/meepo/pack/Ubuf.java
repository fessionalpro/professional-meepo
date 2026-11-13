package pro.fessional.meepo.pack;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Ubuf (user-charset output buffer): a thread-confined Writer bridge to an OutputStream.
 * <p>A lazy CharsetEncoder reads character arrays and String views directly into
 * a reusable byte buffer. One pending high surrogate can span ordinary writes,
 * cached fragments and flush calls. finish completes the character sequence
 * without flushing or closing the caller's stream. close also closes the stream.</p>
 * <p>Complete strings in independent charsets use the JDK encoding fast path.</p>
 * <p>Fixed Utxt fragments use a matching byte cache for known independent charsets.
 * Utxt supplies complete encodings and retains their cache. Other charsets use
 * continuous encoding to preserve byte-order marks and state. Instances belong to
 * one render and one thread.</p>
 */
public final class Ubuf extends Writer {
    // Only known independent encodings may splice cached fragments. Other encodings
    // use one continuous encoder so BOMs and state transitions remain correct.
    private static final Set<Charset> CAN_CACHE = new HashSet<>(Arrays.asList(
        StandardCharsets.UTF_8,
        Charset.forName("GBK"),
        Charset.forName("GB18030"),
        Charset.forName("Big5"),
        Charset.forName("Shift_JIS"),
        Charset.forName("windows-31j"),
        StandardCharsets.UTF_16BE,
        StandardCharsets.UTF_16LE,
        StandardCharsets.US_ASCII,
        StandardCharsets.ISO_8859_1));
    private static final char[] EMPTY = new char[0];
    private final OutputStream out;
    private final Charset charset;
    private final boolean canCache;
    private CharsetEncoder encoder;
    private ByteBuffer byteBuffer;
    private char[] pair;
    private char pending;

    /**
     * Adapt a caller-supplied stream; use finish when the stream must stay open.
     */
    public Ubuf(OutputStream out) {
        this(out, StandardCharsets.UTF_8);
    }

    public Ubuf(OutputStream out, Charset charset) {
        this.out = out;
        this.charset = charset;
        canCache = CAN_CACHE.contains(charset);
    }

    /**
     * Write fixed text using reusable bytes or continuous character encoding.
     */
    public void write(Utxt text) throws IOException {
        if (canCache) {
            byte[] encoded = text.getBytes(charset, true);
            writeEncoded(text.chars, encoded);
        }
        else {
            write(text.chars);
        }
    }

    /**
     * Write characters with their complete encoding, including surrogate replacements.
     */
    private void writeEncoded(char[] chars, byte[] encoded) throws IOException {
        if (chars.length == 0) return;
        int skip = 0;
        if (pending != 0) {
            char high = pending;
            pending = 0;
            if (Character.isLowSurrogate(chars[0])) {
                char[] joined = pair();
                joined[0] = high;
                joined[1] = chars[0];
                encode(CharBuffer.wrap(joined), false);
                skip = encoder().replacement().length;
            }
            else {
                out.write(encoder().replacement());
            }
        }
        int end = encoded.length;
        if (Character.isHighSurrogate(chars[chars.length - 1])) {
            pending = chars[chars.length - 1];
            end -= encoder().replacement().length;
        }
        out.write(encoded, skip, end - skip);
    }

    private CharsetEncoder encoder() {
        if (encoder == null) {
            encoder = charset.newEncoder()
                             .onMalformedInput(CodingErrorAction.REPLACE)
                             .onUnmappableCharacter(CodingErrorAction.REPLACE);
        }
        return encoder;
    }

    private char[] pair() {
        if (pair == null) pair = new char[2];
        return pair;
    }

    private void encode(CharBuffer input) throws IOException {
        while (pending != 0 && input.hasRemaining()) {
            char first = input.get();
            char[] joined = pair();
            joined[0] = pending;
            joined[1] = first;
            pending = 0;
            encode(CharBuffer.wrap(joined), false);
        }
        if (input.hasRemaining()) encode(input, false);
    }

    private void encode(CharBuffer input, boolean end) throws IOException {
        CharsetEncoder current = encoder();
        if (byteBuffer == null) {
            int capacity = Math.max(256, (int) Math.ceil(current.maxBytesPerChar() * 2));
            byteBuffer = ByteBuffer.allocate(capacity);
        }
        CoderResult result;
        do {
            result = current.encode(input, byteBuffer, end);
            drain();
        } while (result.isOverflow());
        if (input.hasRemaining()) pending = input.get();
    }

    private void drain() throws IOException {
        if (byteBuffer.position() == 0) return;
        out.write(byteBuffer.array(), 0, byteBuffer.position());
        byteBuffer.clear();
    }

    @Override
    public void write(String value) throws IOException {
        int length = value.length();
        if (length == 0) return;
        // Complete strings can use the JDK's optimized encoder for stateless output.
        if (canCache && pending == 0 && !Character.isHighSurrogate(value.charAt(length - 1))) {
            out.write(value.getBytes(charset));
        }
        else {
            encode(CharBuffer.wrap(value));
        }
    }

    @Override
    public void write(String value, int offset, int length) throws IOException {
        encode(CharBuffer.wrap(value, offset, offset + length));
    }

    @Override
    public void write(char[] value, int offset, int length) throws IOException {
        encode(CharBuffer.wrap(value, offset, length));
    }

    @Override
    public void write(int value) throws IOException {
        char[] single = pair();
        single[0] = (char) value;
        encode(CharBuffer.wrap(single, 0, 1));
    }

    @Override
    public void flush() throws IOException {
        out.flush();
    }

    @Override
    public void close() throws IOException {
        finish();
        out.close();
    }

    /**
     * Complete the last surrogate without flushing or closing the caller's stream.
     */
    public void finish() throws IOException {
        if (encoder == null) return;
        CharBuffer tail;
        if (pending == 0) {
            tail = CharBuffer.wrap(EMPTY);
        }
        else {
            char[] single = pair();
            single[0] = pending;
            pending = 0;
            tail = CharBuffer.wrap(single, 0, 1);
        }
        encode(tail, true);
        CoderResult result;
        do {
            result = encoder.flush(byteBuffer);
            drain();
        } while (result.isOverflow());
        encoder = null;
    }
}
