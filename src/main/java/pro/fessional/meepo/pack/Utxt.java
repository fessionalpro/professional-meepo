package pro.fessional.meepo.pack;

import java.lang.ref.SoftReference;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;

/**
 * Utxt (user-charset text): fixed characters with a softly retained byte cache.
 * <p>Encoding reads the owned characters through a CharBuffer view. Cached
 * encodings publish complete byte arrays and their charsets together.</p>
 * <p>Safe to share after construction if the transferred char array is never
 * modified. Published byte arrays also remain read-only for their lifetime.</p>
 * <p>Only the latest encoding, including its charset, is softly retained.
 * Each reader holds one complete snapshot while it writes or copies bytes.</p>
 * <p>CharSequence access reads the owned characters without encoding or copying.
 * Subsequences are read-only views.</p>
 */
public final class Utxt implements CharSequence {
    final char[] chars;
    // Publish a completed encoding; concurrent first users may harmlessly encode twice.
    private volatile SoftReference<Encoded> encoded;

    /**
     * Takes ownership of the array; callers must never modify it afterwards.
     */
    public Utxt(char[] chars) {
        this.chars = chars;
    }

    /**
     * Return the owned character array for bulk output. Callers share it read-only.
     */
    public char[] getChars() {
        return chars;
    }

    @Override
    public int length() {
        return chars.length;
    }

    @Override
    public char charAt(int index) {
        return chars[index];
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return CharBuffer.wrap(chars, start, end - start).asReadOnlyBuffer();
    }

    @Override
    public String toString() {
        return new String(chars);
    }

    /**
     * Return a new caller-owned array containing the complete encoding.
     */
    public byte[] getBytes(Charset charset) {
        return getBytes(charset, false);
    }

    /**
     * Encode with the charset's default replacement policy.
     * With caching enabled, return a shared read-only array and retain the latest
     * encoding softly. Otherwise return a new caller-owned array on every call.
     */
    public byte[] getBytes(Charset charset, boolean cache) {
        if (cache) {
            SoftReference<Encoded> reference = encoded;
            Encoded result = reference == null ? null : reference.get();
            if (result != null && charset.equals(result.charset)) return result.bytes;
        }
        ByteBuffer buffer = charset.encode(CharBuffer.wrap(chars));
        byte[] bytes = buffer.array();
        // The encoder estimates capacity from averageBytesPerChar and may grow it.
        // limit marks the encoded bytes, while the backing array can have spare capacity.
        // Trim the unused tail so the returned array contains exactly the encoded text.
        if (buffer.limit() != bytes.length) bytes = Arrays.copyOf(bytes, buffer.limit());
        if (cache) encoded = new SoftReference<>(new Encoded(charset, bytes));
        return bytes;
    }

    /**
     * Publish charset and bytes together; retain only one encoding per fragment.
     */
    private static final class Encoded {
        final Charset charset;
        final byte[] bytes;

        Encoded(Charset charset, byte[] bytes) {
            this.charset = charset;
            this.bytes = bytes;
        }
    }
}
