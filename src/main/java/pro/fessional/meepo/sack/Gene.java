package pro.fessional.meepo.sack;

import org.jetbrains.annotations.NotNull;
import pro.fessional.meepo.bind.Exon;
import pro.fessional.meepo.bind.wow.Clop;
import pro.fessional.meepo.bind.wow.Tock;
import pro.fessional.meepo.pack.Cbuf;
import pro.fessional.meepo.pack.Ubuf;
import pro.fessional.meepo.pack.Utxt;
import pro.fessional.meepo.util.Dent;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.ref.SoftReference;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.nio.charset.Charset;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * A gene: the parsed template, composed of ordered Exon fragments.
 * <p>Parser and Holder assemble the gene once; each merge creates a separate
 * Acid for variables, engines and branch state. Reuse a completed gene, and
 * do not modify its public child nodes after publication. A gene with no runtime
 * engine requirements can cache its complete output.</p>
 *
 * @author trydofor
 * @since 2020-10-15
 */
public class Gene {

    public final long born;
    public final List<Exon> exon;
    public final Set<String> rngs;

    private volatile SoftReference<Text9> text9 = null;
    private volatile int initialCapacity;

    /**
     * Snapshot caller-owned collections; node objects themselves remain shared.
     */
    public Gene(List<Exon> xna, Set<String> rng) {
        this(xna, rng, false);
    }

    /**
     * Internal builders relinquish the completed node list after this call.
     */
    static Gene parsed(List<Exon> xna, Set<String> rng) {
        return new Gene(xna, rng, true);
    }

    private Gene(List<Exon> xna, Set<String> rng, boolean owned) {
        this.exon = Collections.unmodifiableList(owned ? xna : new ArrayList<>(xna));
        // RngChecker supplies a live keySet; detach it from the parse-time engine map.
        this.rngs = Collections.unmodifiableSet(new HashSet<>(rng));
        this.born = System.currentTimeMillis();
        initialCapacity = Math.min(8096, Math.max(128, xna.isEmpty() ? 0 : xna.get(xna.size() - 1).edge.until));
    }

    /**
     * Merge with the context, and output as string.
     */
    @NotNull
    public String merge(Map<String, Object> ctx) {
        final Text9 cached = takeText9();
        if (cached != null) return cached.text7;
        int capacity = initialCapacity;
        String result = merge(ctx, capacity);
        if (!rngs.isEmpty()) {
            int target = Math.min(65536, Math.max(128, result.length()));
            if (target > capacity || target < capacity / 4) initialCapacity = target;
        }
        return result;
    }

    /**
     * Merge with the context, and output as string.
     *
     * @param ctx the context
     * @param len Estimate the size of the result to avoid buffer expansion.
     * @return result
     */
    @NotNull
    public String merge(Map<String, Object> ctx, int len) {
        final Text9 t9 = takeText9();
        if (t9 != null) {
            return t9.text7;
        }

        final Cbuf out = new Cbuf(len);
        merge0(ctx, out);
        final String rst = out.toString();

        if (rngs.isEmpty()) {
            giveText9(rst);
        }
        return rst;
    }

    /**
     * Merge with the context, and output UTF-8 without flushing or closing the stream.
     */
    public void merge(Map<String, Object> ctx, OutputStream out) {
        merge(ctx, out, UTF_8);
    }

    /**
     * Merge with the context, and output bytes in the caller's charset.
     */
    public void merge(Map<String, Object> ctx, OutputStream out, Charset charset) {
        charset = charset == null ? UTF_8 : charset;
        Ubuf writer = new Ubuf(out, charset);
        merge(ctx, writer);
        try {
            writer.finish();
        }
        catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Merge with the context, and output to the Writer.
     */
    public void merge(Map<String, Object> ctx, Writer out) {
        if (rngs.isEmpty()) {
            Dent.write(out, staticText(ctx).content);
            return;
        }

        merge0(ctx, out);
    }

    private Text9 staticText(Map<String, Object> ctx) {
        Text9 cached = takeText9();
        if (cached == null) {
            Cbuf buffer = new Cbuf(initialCapacity);
            merge0(ctx, buffer);
            cached = giveText9(buffer.toString());
        }
        return cached;
    }

    private void merge0(Map<String, Object> ctx, Writer out) {
        // Nodes and warmed plans are reused; variables and branch winners are per merge.
        Acid acid = new Acid(ctx, rngs);
        try {
            for (Exon exon : exon) exon.merge(acid, out);
        }
        finally {
            acid.clear();
        }
    }

    /**
     * Build back to the raw template or escaped text via gene. usually for debugging.
     */
    @NotNull
    public String build() {
        StringWriter buf = new StringWriter(1024);
        for (Exon exon : exon) {
            exon.build(buf);
        }
        return buf.toString();
    }

    /**
     * Print the syntax tree and check for edge continuity.
     * `#` means an intersection
     *
     * @return syntax tree
     */
    public String graph() {
        StringWriter buff = new StringWriter();
        buff.append("Gene, born=").append(Instant.ofEpochMilli(born).toString());
        buff.append(", rngs=").write(String.join(",", rngs));
        buff.append('\n');
        graph(buff, exon, 1, 0);
        return buff.toString();
    }

    /**
     * Print the syntax tree and check for edge continuity.
     * `#` means an intersection
     *
     * @param buff  the buffer to print
     * @param exon  list of exon
     * @param level level, 1-base
     * @param start start point (include), 0-base
     * @return end point of checking (exclude)
     */
    public static int graph(Writer buff, List<Exon> exon, int level, int start) {
        for (Exon ex : exon) {
            Clop edge = ex.edge;
            if (edge.start != start) {
                Dent.treeIt(buff, -level);
            }
            else {
                Dent.treeIt(buff, level);
            }
            try {
                buff.append(ex.toString());
                buff.append('\n');
            }
            catch (IOException e) {
                throw new IllegalStateException(e);
            }

            if (ex instanceof Tock) {
                Tock tock = (Tock) ex;
                start = graph(buff, tock.gene, level + 1, edge.until);
            }
            else {
                start = edge.until;
            }
        }
        return start;
    }

    private Text9 giveText9(String str) {
        Text9 result = new Text9(str);
        text9 = new SoftReference<>(result);
        return result;
    }

    private Text9 takeText9() {
        SoftReference<Text9> reference = text9;
        return reference == null ? null : reference.get();
    }

    /**
     * Softly cached static result in String form and lazily encoded text form.
     */
    private static class Text9 {
        private final String text7;
        private final Utxt content;

        public Text9(String t) {
            this.text7 = t;
            this.content = new Utxt(t.toCharArray());
        }
    }
}
