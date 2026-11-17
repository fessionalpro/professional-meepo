package pro.fessional.meepo.pack;

import org.junit.jupiter.api.Test;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.ReadOnlyBufferException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

class UtxtTest {
    @Test
    void fixedTextProvidesReadOnlyCharacterViews() {
        Utxt text = new Utxt("中文😀".toCharArray());
        assertEquals(4, text.length());
        assertEquals('中', text.charAt(0));
        assertEquals('\ude00', text.charAt(3));
        assertEquals("中文😀", text.toString());
        assertEquals(3, text.codePoints().count());
        assertThrows(IndexOutOfBoundsException.class, () -> text.charAt(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> text.charAt(4));

        CharBuffer view = (CharBuffer) text.subSequence(1, 4);
        assertEquals("文😀", view.toString());
        assertEquals("😀", view.subSequence(1, 3).toString());
        assertTrue(view.isReadOnly());
        assertFalse(view.hasArray());
        assertThrows(ReadOnlyBufferException.class, () -> view.put(0, 'x'));
        assertEquals("", text.subSequence(4, 4).toString());
        assertThrows(IndexOutOfBoundsException.class, () -> text.subSequence(-1, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> text.subSequence(2, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> text.subSequence(0, 5));

        Utxt empty = new Utxt(new char[0]);
        assertEquals(0, empty.length());
        assertEquals("", empty.toString());
        assertEquals("", empty.subSequence(0, 0).toString());
        assertThrows(IndexOutOfBoundsException.class, () -> empty.charAt(0));
    }

    @Test
    void staticCachesAreKeyedByCharset() throws Exception {
        Utxt text = new Utxt("中文😀".toCharArray());
        Charset gbk = Charset.forName("GBK");
        byte[] first = text.getBytes(gbk, true);
        assertArrayEquals("中文😀".getBytes(gbk), first);
        assertSame(first, text.getBytes(Charset.forName("cp936"), true));
        byte[] second = text.getBytes(UTF_8, true);
        assertArrayEquals("中文😀".getBytes(UTF_8), second);
        assertSame(second, text.getBytes(UTF_8, true));
        assertNotSame(first, text.getBytes(gbk, true));

        Utxt empty = new Utxt(new char[0]);
        byte[] bytes = empty.getBytes(UTF_8, true);
        assertEquals(0, bytes.length);
        assertSame(bytes, empty.getBytes(UTF_8, true));
        assertThrows(NoSuchMethodException.class, () -> Utxt.class.getMethod("write", java.io.Writer.class));
        assertThrows(NoSuchMethodException.class, () -> Utxt.class.getMethod("setCacheBytes", byte[].class, Charset.class));
        assertThrows(NoSuchMethodException.class, () -> Utxt.class.getMethod("getCachedBytes", Charset.class));
    }

    @Test
    void completeEncodingsMatchStringsAcrossCharsetsAndMalformedBoundaries() {
        String[] values = { "", "abc", "中文", "😀", "\ud800", "\udfff", "\ud800\ud800\udfff\udfff",
                            "\u0000é日本😀\ud800", String.join("", java.util.Collections.nCopies(1000, "中文😀")) };
        for (String value : values) {
            Utxt text = new Utxt(value.toCharArray());
            for (String name : new String[]{ "UTF-8", "GBK", "GB18030", "Big5", "Shift_JIS", "windows-31j",
                                             "US-ASCII", "ISO-8859-1", "UTF-16LE", "UTF-16BE", "UTF-16", "ISO-2022-JP" }) {
                Charset charset = Charset.forName(name);
                byte[] expected = value.getBytes(charset);
                assertArrayEquals(expected, text.getBytes(charset), name);
                assertArrayEquals(expected, text.getBytes(charset, true), name);
                assertArrayEquals(expected, text.getBytes(charset, false), name);
            }
        }
    }

    @Test
    void uncachedEncodingReturnsOwnedArraysAndPreservesTheLatestCache() throws Exception {
        Utxt text = new Utxt("中文😀\ud800".toCharArray());
        java.lang.reflect.Field slot = Utxt.class.getDeclaredField("encoded");
        slot.setAccessible(true);
        byte[] first = text.getBytes(UTF_8);
        assertNull(slot.get(text));
        byte[] cached = text.getBytes(UTF_8, true);
        Object reference = slot.get(text);
        byte[] second = text.getBytes(UTF_8);
        assertNotSame(first, second);
        assertNotSame(cached, second);
        assertArrayEquals(cached, second);
        second[0] ^= 1;
        assertArrayEquals("中文😀\ud800".getBytes(UTF_8), cached);
        Charset gbk = Charset.forName("GBK");
        assertArrayEquals("中文😀\ud800".getBytes(gbk), text.getBytes(gbk, false));
        assertSame(reference, slot.get(text));
        assertSame(cached, text.getBytes(UTF_8, true));
    }

    @Test
    void encodingReadsTheOwnedCharacterArray() {
        char[] input = { 'a', 'b', 'c' };
        AtomicInteger encodes = new AtomicInteger();
        Charset charset = new Charset("X-Meepo-Utxt-Views", new String[0]) {
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
                return new CharsetEncoder(this, 1, 1) {
                    @Override
                    public boolean isLegalReplacement(byte[] bytes) {
                        return true;
                    }

                    @Override
                    protected CoderResult encodeLoop(CharBuffer source, ByteBuffer target) {
                        assertSame(input, source.array());
                        encodes.incrementAndGet();
                        while (source.hasRemaining()) {
                            if (!target.hasRemaining()) return CoderResult.OVERFLOW;
                            target.put((byte) source.get());
                        }
                        return CoderResult.UNDERFLOW;
                    }
                };
            }
        };
        Utxt text = new Utxt(input);
        assertSame(input, text.getChars());
        byte[] cached = text.getBytes(charset, true);
        assertArrayEquals(new byte[]{ 'a', 'b', 'c' }, cached);
        assertSame(cached, text.getBytes(charset, true));
        assertEquals(1, encodes.get());
        byte[] fresh = text.getBytes(charset, false);
        assertArrayEquals(cached, fresh);
        assertNotSame(cached, fresh);
        assertEquals(2, encodes.get());
    }

    @Test
    void concurrentDifferentCharsetsCannotMixTheCachedBytes() throws Exception {
        Utxt text = new Utxt("中文😀".toCharArray());
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
                        assertArrayEquals(expected, text.getBytes(charset, i % 2 == 0));
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
    void clearedSoftEntriesRebuildWithoutDependingOnGcTiming() throws Exception {
        Utxt text = new Utxt("中文".toCharArray());
        byte[] first = text.getBytes(UTF_8, true);
        java.lang.reflect.Field slot = Utxt.class.getDeclaredField("encoded");
        slot.setAccessible(true);
        Reference<?> reference = (Reference<?>) slot.get(text);
        assertInstanceOf(SoftReference.class, reference);
        reference.clear();
        byte[] second = text.getBytes(UTF_8, true);
        assertSame(second, text.getBytes(UTF_8, true));
        assertNotSame(first, second);
        assertArrayEquals(first, second);
        Object entry = ((Reference<?>) slot.get(text)).get();
        java.lang.reflect.Field key = entry.getClass().getDeclaredField("charset");
        key.setAccessible(true);
        assertSame(UTF_8, key.get(entry));
    }
}
