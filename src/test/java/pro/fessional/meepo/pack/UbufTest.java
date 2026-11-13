package pro.fessional.meepo.pack;

import org.junit.jupiter.api.Test;

import pro.fessional.meepo.sack.Gene;
import pro.fessional.meepo.sack.Parser;
import pro.fessional.meepo.util.Dent;

import java.io.*;
import java.lang.ref.Reference;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

class UbufTest {
    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void fixedFragmentsCacheOnlyIndependentOutputCharsets() throws Exception {
        java.lang.reflect.Field slot = Utxt.class.getDeclaredField("encoded");
        slot.setAccessible(true);
        for (String value : new String[]{ "", "中文😀", "中文\ud800" }) {
            Utxt text = new Utxt(value.toCharArray());
            for (String name : new String[]{ "UTF-8", "GBK", "UTF-16LE", "UTF-16", "ISO-2022-JP" }) {
                Charset charset = Charset.forName(name);
                Object previous = slot.get(text);
                byte[] cached = null;
                for (int render = 0; render < 2; render++) {
                    ByteArrayOutputStream stream = new ByteArrayOutputStream();
                    Ubuf writer = new Ubuf(stream, charset);
                    Dent.write(writer, text);
                    writer.finish();
                    assertArrayEquals(value.getBytes(charset), stream.toByteArray(), name);
                    if (name.equals("UTF-16") || name.equals("ISO-2022-JP")) {
                        assertSame(previous, slot.get(text));
                    }
                    else if (render == 0) {
                        Object published = slot.get(text);
                        assertNotNull(published);
                        cached = text.getBytes(charset, true);
                        assertSame(published, slot.get(text));
                        assertArrayEquals(value.getBytes(charset), cached);
                    }
                    else {
                        assertSame(cached, text.getBytes(charset, true));
                    }
                }
            }
            Reference<?> previous = (Reference<?>) slot.get(text);
            previous.clear();
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            Ubuf writer = new Ubuf(stream);
            Dent.write(writer, text);
            writer.finish();
            assertArrayEquals(value.getBytes(UTF_8), stream.toByteArray());
            assertNotSame(previous, slot.get(text));
            assertArrayEquals(value.getBytes(UTF_8), text.getBytes(UTF_8, true));
        }
    }

    @Test
    void concurrentWritersReuseOneTextAcrossCharsets() throws Exception {
        Utxt text = new Utxt("中文😀\ud800".toCharArray());
        CyclicBarrier start = new CyclicBarrier(4);
        ExecutorService workers = Executors.newFixedThreadPool(4);
        try {
            List<Future<?>> results = new ArrayList<>();
            for (String name : new String[]{ "UTF-8", "GBK", "UTF-16LE", "ISO-2022-JP" }) {
                results.add(workers.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    Charset charset = Charset.forName(name);
                    byte[] expected = text.toString().getBytes(charset);
                    for (int i = 0; i < 200; i++) {
                        ByteArrayOutputStream stream = new ByteArrayOutputStream();
                        Ubuf writer = new Ubuf(stream, charset);
                        Dent.write(writer, text);
                        writer.finish();
                        assertArrayEquals(expected, stream.toByteArray());
                    }
                    return null;
                }));
            }
            for (Future<?> result : results) result.get(30, TimeUnit.SECONDS);
        }
        finally {
            workers.shutdownNow();
        }
    }

    @Test
    void arbitraryUtf16ChunkingMatchesEncodingTheWholeString() throws Exception {
        Random random = new Random(20261105);
        char[] alphabet = { 'a', '\u0000', '中', '\u07ff', '\u0800', '\ud83d', '\ude00', '\ud800', '\udfff' };
        for (int trial = 0; trial < 500; trial++) {
            StringBuilder expected = new StringBuilder();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            Ubuf writer = new Ubuf(output);
            for (int chunk = 0; chunk < 40; chunk++) {
                char[] value = new char[random.nextInt(10)];
                for (int i = 0; i < value.length; i++) value[i] = alphabet[random.nextInt(alphabet.length)];
                expected.append(value);
                switch (random.nextInt(4)) {
                    case 0:
                        Dent.write(writer, new Utxt(value));
                        break;
                    case 1:
                        writer.write(value, 0, value.length);
                        break;
                    case 2:
                        writer.write("[" + new String(value) + "]", 1, value.length);
                        break;
                    default:
                        for (char c : value) writer.write(c);
                }
                writer.flush();
            }
            writer.finish();
            writer.finish();
            assertArrayEquals(bytes(expected.toString()), output.toByteArray(), "trial " + trial);
        }
    }

    @Test
    void cachedStaticTextAndStreamsKeepTheirContracts() throws Exception {
        Utxt text = new Utxt("中文😀".toCharArray());
        StringWriter chars = new StringWriter();
        Dent.write(chars, text);
        assertEquals("中文😀", chars.toString());
        java.lang.reflect.Field slot = Utxt.class.getDeclaredField("encoded");
        slot.setAccessible(true);
        assertNull(slot.get(text));
        class Stream extends ByteArrayOutputStream {
            boolean closed;
            boolean flushed;

            @Override
            public void close() {
                closed = true;
            }

            @Override
            public void flush() {
                flushed = true;
            }
        }
        Stream stream = new Stream();
        Ubuf writer = new Ubuf(stream);
        Dent.write(writer, text);
        writer.write("\ud800");
        writer.close();
        assertTrue(stream.closed);
        assertFalse(stream.flushed);
        assertArrayEquals(bytes("中文😀\ud800"), stream.toByteArray());
        assertThrows(IndexOutOfBoundsException.class, () -> writer.write(new char[0], 1, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> writer.write("x", -1, 1));
        OutputStream failure = new OutputStream() {
            public void write(int value) throws IOException {
                throw new IOException("write");
            }
        };
        assertThrows(IllegalStateException.class, () -> Dent.write(new Ubuf(failure), text));
    }

    @Test
    void arraySlicesAndStringViewsReachTheEncoderWithoutCopies() throws Exception {
        EncodingCharset charset = new EncodingCharset(1, new byte[0]);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Ubuf writer = new Ubuf(bytes, charset);
        char[] input = { '[', 'a', 'b', 'c', ']' };
        writer.write(input, 1, 3);
        writer.write("[de]", 1, 2);
        writer.write('f');
        writer.write('g');
        writer.finish();
        assertArrayEquals("abcdefg".getBytes(UTF_8), bytes.toByteArray());
        assertSame(input, charset.inputs.get(0));
        assertNull(charset.inputs.get(1));
        assertSame(charset.inputs.get(2), charset.inputs.get(3));
        assertThrows(IndexOutOfBoundsException.class, () -> writer.write(input, Integer.MAX_VALUE, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> writer.write("x", Integer.MAX_VALUE, 1));
    }

    @Test
    void encoderHandlesWideCharactersAndLargeFinalOutput() throws Exception {
        byte[] footer = new byte[1300];
        Arrays.fill(footer, (byte) '!');
        for (int width : new int[]{ 1, 1500 }) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            Ubuf writer = new Ubuf(bytes, new EncodingCharset(width, footer));
            writer.finish();
            assertEquals(0, bytes.size());
            writer.write(new char[]{ 'a', 'b', 'c' }, 0, 3);
            writer.flush();
            assertEquals(3 * width, bytes.size());
            writer.finish();
            writer.finish();
            byte[] expected = new byte[3 * width + footer.length];
            for (int i = 0; i < 3; i++) Arrays.fill(expected, i * width, (i + 1) * width, (byte) ('a' + i));
            System.arraycopy(footer, 0, expected, 3 * width, footer.length);
            assertArrayEquals(expected, bytes.toByteArray());
            writer.write('d');
            writer.finish();
            byte[] continued = Arrays.copyOf(expected, expected.length + width + footer.length);
            Arrays.fill(continued, expected.length, expected.length + width, (byte) 'd');
            System.arraycopy(footer, 0, continued, expected.length + width, footer.length);
            assertArrayEquals(continued, bytes.toByteArray());
        }
    }

    @Test
    void chunkedOutputMatchesOneEncodingAcrossCharsetsAndSurrogateBoundaries() throws Exception {
        for (String name : Arrays.asList("UTF-8", "GBK", "GB18030", "Big5", "Shift_JIS", "windows-31j",
            "US-ASCII", "ISO-8859-1", "UTF-16LE", "UTF-16BE", "UTF-16", "ISO-2022-JP")) {
            Charset charset = Charset.forName(name);
            Random random = new Random(20261108L);
            char[] alphabet = { 'x', '\u0000', '中', '日', 'é', '\ud83d', '\ude00', '\ud800', '\udfff' };
            for (int trial = 0; trial < 80; trial++) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                Ubuf output = new Ubuf(bytes, charset);
                StringBuilder expected = new StringBuilder();
                for (int chunk = 0; chunk < 24; chunk++) {
                    char[] value = new char[random.nextInt(7)];
                    for (int i = 0; i < value.length; i++) value[i] = alphabet[random.nextInt(alphabet.length)];
                    expected.append(value);
                    switch (random.nextInt(3)) {
                        case 0:
                            Dent.write(output, new Utxt(value));
                            break;
                        case 1:
                            output.write(new String(value));
                            break;
                        default:
                            for (char c : value) output.write(c);
                    }
                    output.flush();
                }
                output.finish();
                output.finish();
                assertArrayEquals(expected.toString().getBytes(charset), bytes.toByteArray(), name + " trial " + trial);
            }
        }
    }

    @Test
    void continuousEncodersFinishWithoutClosingOrFlushingTheCaller() throws Exception {
        class Stream extends ByteArrayOutputStream {
            boolean closed, flushed;

            @Override
            public void close() {
                closed = true;
            }

            @Override
            public void flush() {
                flushed = true;
            }
        }
        for (String name : Arrays.asList("GBK", "UTF-16", "ISO-2022-JP")) {
            Stream stream = new Stream();
            Gene gene = Parser.parse("# HI-MEEPO\n# RNA:USE /VALUE/value/*\n日VALUE\n");
            gene.merge(Collections.singletonMap("value", "本\ud800"), stream, Charset.forName(name));
            assertArrayEquals(gene.merge(Collections.singletonMap("value", "本\ud800")).getBytes(Charset.forName(name)), stream.toByteArray());
            assertFalse(stream.closed);
            assertFalse(stream.flushed);
        }
        OutputStream broken = new OutputStream() {
            @Override
            public void write(int value) throws IOException {
                throw new IOException("fixture");
            }
        };
        Gene gene = Parser.parse("# HI-MEEPO\nplain");
        assertThrows(IllegalStateException.class, () -> gene.merge(Collections.emptyMap(), broken, Charset.forName("UTF-16")));
    }

    /**
     * Encoding-only fixture that exposes input views and can emit a multi-buffer footer.
     */
    private static final class EncodingCharset extends Charset {
        final int width;
        final byte[] footer;
        final List<char[]> inputs = new ArrayList<>();

        EncodingCharset(int width, byte[] footer) {
            super("X-Meepo-Views", new String[0]);
            this.width = width;
            this.footer = footer;
        }

        @Override
        public boolean contains(Charset other) {
            return other == this;
        }

        @Override
        public CharsetDecoder newDecoder() {
            throw new UnsupportedOperationException();
        }

        @Override
        public CharsetEncoder newEncoder() {
            return new CharsetEncoder(this, width, width) {
                int footerOffset;

                @Override
                public boolean isLegalReplacement(byte[] bytes) {
                    return true;
                }

                @Override
                protected CoderResult encodeLoop(CharBuffer input, ByteBuffer output) {
                    if (input.hasRemaining()) inputs.add(input.hasArray() ? input.array() : null);
                    while (input.hasRemaining()) {
                        if (output.remaining() < width) return CoderResult.OVERFLOW;
                        byte value = (byte) input.get();
                        for (int i = 0; i < width; i++) output.put(value);
                    }
                    return CoderResult.UNDERFLOW;
                }

                @Override
                protected CoderResult implFlush(ByteBuffer output) {
                    int length = Math.min(output.remaining(), footer.length - footerOffset);
                    output.put(footer, footerOffset, length);
                    footerOffset += length;
                    return footerOffset == footer.length ? CoderResult.UNDERFLOW : CoderResult.OVERFLOW;
                }
            };
        }
    }
}
