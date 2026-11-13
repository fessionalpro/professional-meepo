package pro.fessional.meepo;

import org.jetbrains.annotations.NotNull;
import pro.fessional.meepo.sack.Gene;
import pro.fessional.meepo.sack.Holder;
import pro.fessional.meepo.sack.Parser;
import pro.fessional.meepo.util.Read;

import java.io.File;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Map;
import java.util.Objects;
import java.util.Arrays;
import java.nio.charset.Charset;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static java.nio.charset.StandardCharsets.UTF_8;


/**
 * Entry point for loading and caching parsed template genes.
 * Parser handles comment directives and Holder handles placeholders; a Gene
 * performs each merge with its own runtime context.
 * {@code ttl} is a cache duration in seconds: negative never expires, zero bypasses caching.
 *
 * <h2>Measured rendering performance</h2>
 * <p>Meepo provides competitive rendering performance while preserving the target
 * language's template syntax. The following historical local snapshot was measured on
 * 2026-11-05 with JMH 1.37 and Temurin JDK 21: one thread, a 256 MiB heap,
 * three one-second warmup iterations and five one-second measurement iterations
 * in each of two independent JVMs. Values are mean complete renders per second
 * (higher is better); templates are parsed or compiled before timing.</p>
 *
 * <table>
 * <caption>Representative hot-render throughput, ops/s</caption>
 * <tr><th>Workload / output</th><th>Meepo</th><th>jte 3.2.5</th>
 *     <th>JMustache 1.16</th><th>Mustache.java 0.9.14</th></tr>
 * <tr><td>Two properties / default String</td><td>3,038,361</td><td>953,871</td>
 *     <td>2,391,203</td><td>2,090,645</td></tr>
 * <tr><td>Static Unicode / owned UTF-8 byte array</td><td>8,318,673</td><td>3,117,783</td>
 *     <td>275,879</td><td>261,522</td></tr>
 * <tr><td>200 conditional rows / String, capacity 4096</td><td>18,764</td><td>33,683</td>
 *     <td>21,498</td><td>21,732</td></tr>
 * </table>
 *
 * <p>The comparison engines are pinned, verified official-tag source builds and
 * test-only dependencies. Setup checks identical characters and UTF-8 bytes,
 * including Chinese text, emoji and raw HTML characters. Computed model properties
 * and predicates execute during rendering. jte uses typed Java calls and enables
 * binary static content for byte output. Output-buffer defaults and capacities
 * differ between APIs, so comparisons must stay within a workload and output mode.</p>
 *
 * <p>Compared with the already optimized implementation before this round,
 * the default short-template String throughput was 3.9 times as high, and
 * static UTF-8 throughput was 22.9 times as high. Short-template allocation
 * fell from approximately 25.1 KB to 1.3 KB per render. These measurements support
 * efficient practical use, rather than a claim that one engine wins every workload:
 * jte leads dynamic long-loop rendering, and Meepo's 200-row default String case
 * had a 10.7 percent lower mean in this batch. Fixed-capacity String rendering
 * for that case was approximately unchanged.</p>
 *
 * <p>Static String cache hits return an existing immutable String and are distinct
 * from copying output. {@link Gene#merge(Map, java.io.OutputStream)} caches
 * encoded static text; byte-array benchmark adapters live in test sources.
 * Production remains Java 8-compatible
 * without adding runtime dependencies; running jte comparisons requires Java 17.</p>
 *
 * <p>Benchmark sources and comparison dependencies live in the test build.
 * The repository keeps no raw measurement files or cached benchmark runs;
 * these historical values do not replace a fresh measurement of a new revision.
 * Methodology, correctness gates and execution instructions are in the
 * <a href="https://github.com/trydofor/professional-meepo/blob/main/doc/prompt/jmh.prompt.md">JMH execution prompt</a>.</p>
 *
 * @author trydofor
 * @since 2020-10-15
 */
public class Meepo {

    public static final int CACHE_ALWAYS = -1;
    public static final int CACHE_NEVER = 0;
    public static final int CACHE_MINUTE = 60;
    public static final int CACHE_HOUR = CACHE_MINUTE * 60;
    public static final int CACHE_DAY = CACHE_HOUR * 24;
    public static final int CACHE_MONTH = CACHE_DAY * 30;

    private static final ConcurrentHashMap<Object, Gene> CACHING = new ConcurrentHashMap<>();

    /**
     * Parse form the URI with ttl=CACHE_ALWAYS
     * URI support `file://`,`classpath:`,`http://`
     */
    @NotNull
    public static Gene parse(@NotNull String uri) {
        return parse(uri, CACHE_ALWAYS);
    }

    /**
     * Parse form the URI with the given ttl.
     * URI support `file://`,`classpath:`,`http://`
     */
    @NotNull
    public static Gene parse(@NotNull String uri, int ttl) {
        return parse(uri, UTF_8, ttl);
    }

    public static Gene parse(@NotNull String uri, Charset charset) {
        return parse(uri, charset, CACHE_ALWAYS);
    }

    public static Gene parse(@NotNull String uri, Charset charset, int ttl) {
        return cache0(sourceKey(uri, charset), () -> parse0(uri, charset), ttl);
    }

    /**
     * Parses text directly and cache by key with ttl=CACHE_ALWAYS
     */
    @NotNull
    public static Gene parse(@NotNull String key, @NotNull String txt) {
        return parse(key, txt, CACHE_ALWAYS);
    }

    /**
     * Parses text directly and cache by key with the given ttl.
     */
    @NotNull
    public static Gene parse(@NotNull String key, @NotNull String txt, int ttl) {
        return ttl == 0 ? Parser.parse(txt) : cache(key, () -> Parser.parse(txt), ttl);
    }

    /**
     * Parses from file and cache by its path with ttl=CACHE_ALWAYS
     */
    @NotNull
    public static Gene parse(@NotNull File file) {
        return parse(file, CACHE_ALWAYS);
    }

    /**
     * Parses from file and cache by its path with the given ttl.
     */
    @NotNull
    public static Gene parse(@NotNull File file, int ttl) {
        return parse(file, UTF_8, ttl);
    }

    public static Gene parse(@NotNull File file, Charset charset) {
        return parse(file, charset, CACHE_ALWAYS);
    }

    public static Gene parse(@NotNull File file, Charset charset, int ttl) {
        return cache0(sourceKey(file.getAbsolutePath(), charset), () -> parse0(file, charset), ttl);
    }

    /**
     * Parses from stream and cache by its path with ttl=CACHE_ALWAYS
     */
    @NotNull
    public static Gene parse(@NotNull String key, @NotNull InputStream ins) {
        return parse(key, ins, CACHE_ALWAYS);
    }

    /**
     * Parses from stream and cache by its path with the given ttl.
     */
    @NotNull
    public static Gene parse(@NotNull String key, @NotNull InputStream ins, int ttl) {
        return parse(key, ins, UTF_8, ttl);
    }

    public static Gene parse(@NotNull String key, @NotNull InputStream ins, Charset charset) {
        return parse(key, ins, charset, CACHE_ALWAYS);
    }

    public static Gene parse(@NotNull String key, @NotNull InputStream ins, Charset charset, int ttl) {
        return cache0(sourceKey(key, charset), () -> parse0(ins, charset), ttl);
    }

    /**
     * Non-default source encodings use typed keys, isolated from caller-defined String cache keys.
     */
    private static Object sourceKey(String key, Charset charset) {
        return charset == null || UTF_8.equals(charset) ? key : Arrays.asList(key, charset.name());
    }

    /////////////

    /**
     * Parse and cache (ttl=CACHE_ALWAYS) the template, then merge with the context.
     *
     * @param ctx the context
     * @param txt placeholder template
     * @return result
     * @see #piece(Map, String, int)
     */
    @NotNull
    public static String piece(@NotNull Map<String, Object> ctx, @NotNull String txt) {
        return piece(ctx, txt, CACHE_ALWAYS);
    }

    /**
     * Parse and cache the template, then merge with the context.
     *
     * @param ctx the context
     * @param txt placeholder template
     * @param ttl cache ttl
     * @return result
     * @see #parse(String, int)
     * @see pro.fessional.meepo.sack.Holder#parse(String)
     */
    @NotNull
    public static String piece(@NotNull Map<String, Object> ctx, @NotNull String txt, int ttl) {
        final Gene gene = ttl == 0 ? Holder.parse(txt) : cache(txt, () -> Holder.parse(txt), ttl);
        return gene.merge(ctx);
    }


    @NotNull
    public static Gene cache(@NotNull String key, @NotNull Supplier<Gene> gen, int ttl) {
        return cache0(key, gen, ttl);
    }

    private static Gene cache0(Object key, Supplier<Gene> gen, int ttl) {
        if (ttl == CACHE_NEVER) return Objects.requireNonNull(gen.get());
        final Gene gene;
        if (ttl < 0) {
            gene = CACHING.computeIfAbsent(key, s -> gen.get());
        }
        else {
            final long born = System.currentTimeMillis() - ttl * 1000L;
            gene = CACHING.compute(key, (s, e) -> (e != null && born <= e.born) ? e : gen.get());
        }
        return Objects.requireNonNull(gene);
    }

    /**
     * Parse and cache (ttl=CACHE_ALWAYS) the template form URI, then merge with the context.
     *
     * @see #parse(String, int)
     */
    @NotNull
    public static String merge(@NotNull Map<String, Object> ctx, @NotNull String uri) {
        return merge(ctx, uri, CACHE_ALWAYS);
    }

    /**
     * Parse and cache the template form URI, then merge with the context.
     *
     * @see #parse(String, int)
     */
    @NotNull
    public static String merge(@NotNull Map<String, Object> ctx, @NotNull String uri, int ttl) {
        Gene gene = parse(uri, ttl);
        return gene.merge(ctx);
    }

    public static String merge(@NotNull Map<String, Object> ctx, @NotNull String uri, Charset charset) {
        return merge(ctx, uri, charset, CACHE_ALWAYS);
    }

    public static String merge(@NotNull Map<String, Object> ctx, @NotNull String uri, Charset charset, int ttl) {
        return parse(uri, charset, ttl).merge(ctx);
    }

    /**
     * Parse and cache (ttl=CACHE_ALWAYS) the template form stream, then merge with the context.
     *
     * @see #parse(String, InputStream, int)
     */
    @NotNull
    public static String merge(@NotNull Map<String, Object> ctx, @NotNull String key, @NotNull InputStream ins) {
        return merge(ctx, key, ins, CACHE_ALWAYS);
    }

    /**
     * Parse and cache the template form stream, then merge with the context.
     *
     * @see #parse(String, InputStream, int)
     */
    @NotNull
    public static String merge(@NotNull Map<String, Object> ctx, @NotNull String key, @NotNull InputStream ins, int ttl) {
        Gene gene = parse(key, ins, ttl);
        return gene.merge(ctx);
    }

    public static String merge(@NotNull Map<String, Object> ctx, @NotNull String key, @NotNull InputStream ins, Charset charset) {
        return merge(ctx, key, ins, charset, CACHE_ALWAYS);
    }

    public static String merge(@NotNull Map<String, Object> ctx, @NotNull String key, @NotNull InputStream ins, Charset charset, int ttl) {
        return parse(key, ins, charset, ttl).merge(ctx);
    }

    /**
     * Parse and cache (ttl=CACHE_ALWAYS) the template text, then merge with the context.
     *
     * @see #parse(String, InputStream, int)
     */
    @NotNull
    public static String merge(@NotNull Map<String, Object> ctx, @NotNull String key, @NotNull String txt) {
        return merge(ctx, key, txt, CACHE_ALWAYS);
    }

    /**
     * Parse and cache the template text, then merge with the context.
     *
     * @see #parse(String, InputStream, int)
     */
    @NotNull
    public static String merge(@NotNull Map<String, Object> ctx, @NotNull String key, @NotNull String txt, int ttl) {
        Gene gene = parse(key, txt, ttl);
        return gene.merge(ctx);
    }

    /**
     * Parse and cache (ttl=CACHE_ALWAYS) the template form file, then merge with the context.
     *
     * @see #parse(String, InputStream, int)
     */
    @NotNull
    public static String merge(@NotNull Map<String, Object> ctx, @NotNull File file) {
        return merge(ctx, file, CACHE_ALWAYS);
    }

    /**
     * Parse and cache the template form file, then merge with the context.
     *
     * @see #parse(String, InputStream, int)
     */
    @NotNull
    public static String merge(@NotNull Map<String, Object> ctx, @NotNull File file, int ttl) {
        Gene gene = parse(file, ttl);
        return gene.merge(ctx);
    }

    public static String merge(@NotNull Map<String, Object> ctx, @NotNull File file, Charset charset) {
        return merge(ctx, file, charset, CACHE_ALWAYS);
    }

    public static String merge(@NotNull Map<String, Object> ctx, @NotNull File file, Charset charset, int ttl) {
        return parse(file, charset, ttl).merge(ctx);
    }
    /////////////

    /**
     * Parse and cache (ttl=CACHE_ALWAYS) the template form URI, then merge with the context to stream.
     *
     * @see #parse(String, int)
     */
    @NotNull
    public static InputStream steam(@NotNull Map<String, Object> ctx, @NotNull String uri) {
        return steam(ctx, uri, CACHE_ALWAYS);
    }

    /**
     * Parse and cache the template form URI, then merge with the context to stream.
     *
     * @see #parse(String, int)
     */
    @NotNull
    public static InputStream steam(@NotNull Map<String, Object> ctx, @NotNull String uri, int ttl) {
        Gene gene = parse(uri, ttl);
        return steam(ctx, gene);
    }

    /**
     * Parse and cache (ttl=CACHE_ALWAYS) the template form stream, then merge with the context to stream.
     *
     * @see #parse(String, InputStream, int)
     */
    @NotNull
    public static InputStream steam(@NotNull Map<String, Object> ctx, @NotNull String key, @NotNull InputStream ins) {
        return steam(ctx, key, ins, CACHE_ALWAYS);
    }

    /**
     * Parse and cache the template form stream, then merge with the context to stream.
     *
     * @see #parse(String, InputStream, int)
     */
    @NotNull
    public static InputStream steam(@NotNull Map<String, Object> ctx, @NotNull String key, @NotNull InputStream ins, int ttl) {
        Gene gene = parse(key, ins, ttl);
        return steam(ctx, gene);
    }

    /**
     * Parse and cache (ttl=CACHE_ALWAYS) the template text, then merge with the context to stream.
     *
     * @see #parse(String, InputStream, int)
     */
    @NotNull
    public static InputStream steam(@NotNull Map<String, Object> ctx, @NotNull String key, final String txt) {
        return steam(ctx, key, txt, CACHE_ALWAYS);
    }

    /**
     * Parse and cache the template text, then merge with the context to stream.
     *
     * @see #parse(String, InputStream, int)
     */
    @NotNull
    public static InputStream steam(@NotNull Map<String, Object> ctx, @NotNull String key, @NotNull String txt, int ttl) {
        Gene gene = parse(key, txt, ttl);
        return steam(ctx, gene);
    }

    /**
     * Parse and cache (ttl=CACHE_ALWAYS) the template from file, then merge with the context to stream.
     *
     * @see #parse(String, InputStream, int)
     */
    @NotNull
    public static InputStream steam(@NotNull Map<String, Object> ctx, @NotNull File file) {
        return steam(ctx, file, CACHE_ALWAYS);
    }

    /**
     * Parse and cache the template from file, then merge with the context to stream.
     *
     * @see #parse(String, InputStream, int)
     */
    @NotNull
    public static InputStream steam(@NotNull Map<String, Object> ctx, @NotNull File file, int ttl) {
        Gene gene = parse(file, ttl);
        return steam(ctx, gene);
    }

    /**
     * Merge the Gene to UTF-8 bytes.
     *
     * @see #parse(String, InputStream, int)
     */
    @NotNull
    public static InputStream steam(@NotNull Map<String, Object> ctx, @NotNull Gene gene) {
        return steam(ctx, gene, UTF_8);
    }

    public static InputStream steam(@NotNull Map<String, Object> ctx, @NotNull Gene gene, Charset charset) {
        ByteStream out = new ByteStream(1024);
        gene.merge(ctx, out, charset);
        return out.stream();
    }

    /**
     * Transfer a completed render's buffer; this producer is never reused.
     */
    private static final class ByteStream extends ByteArrayOutputStream {
        ByteStream(int capacity) {
            super(capacity);
        }

        InputStream stream() {
            return new ByteArrayInputStream(buf, 0, count);
        }
    }

    ////////////

    /**
     * No cache, only parse template from URI support `file://`,`classpath:`,`http://`
     */
    @NotNull
    private static Gene parse0(String uri, Charset charset) {
        String txt = Read.read(uri, null, charset);
        return Parser.parse(txt, uri, false, charset);
    }


    /**
     * No cache, only parse template from file
     */
    @NotNull
    private static Gene parse0(File file, Charset charset) {
        try {
            String txt = Read.read(new FileInputStream(file), charset);
            return Parser.parse(txt, file.getAbsolutePath(), false, charset);
        }
        catch (FileNotFoundException e) {
            throw new IllegalStateException("failed to load file=" + file, e);
        }
    }

    /**
     * No cache, only parse template from stream
     */
    @NotNull
    private static Gene parse0(InputStream ins, Charset charset) {
        return Parser.parse(ins, charset);
    }
}
