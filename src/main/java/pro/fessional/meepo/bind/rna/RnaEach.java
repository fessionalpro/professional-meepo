package pro.fessional.meepo.bind.rna;

import org.jetbrains.annotations.NotNull;
import pro.fessional.meepo.bind.Exon;
import pro.fessional.meepo.bind.kin.Rng;
import pro.fessional.meepo.bind.wow.Clop;
import pro.fessional.meepo.bind.wow.Tock;
import pro.fessional.meepo.poof.RnaEngine;
import pro.fessional.meepo.poof.RnaWarmed;
import pro.fessional.meepo.poof.RngChecker;
import pro.fessional.meepo.sack.Acid;
import pro.fessional.meepo.util.Dent;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.RandomAccess;
import java.util.stream.BaseStream;

import static pro.fessional.meepo.bind.Const.BLT$EACH_COUNT;
import static pro.fessional.meepo.bind.Const.BLT$EACH_FIRST;
import static pro.fessional.meepo.bind.Const.BLT$EACH_LAST;
import static pro.fessional.meepo.bind.Const.BLT$EACH_TOTAL;
import static pro.fessional.meepo.bind.Const.OBJ$NAVI_DOT;

/**
 * RNA:EACH repeats its child gene over a collection, array or finite cursor.
 * The step controls direction and stride. Item metadata lives in the merge-local
 * context. An empty input leaves its named group available to RNA:ELSE.
 * <p>Iterator, non-Collection Iterable and BaseStream inputs are collected before
 * rendering, then use the same size, count and direction semantics as lists.
 * Skipped positions hold null to release unused values while preserving indices.
 * Reverse selection is determined after the input size is known.
 * The caller owns stream closure.</p>
 *
 * <pre>
 * ` &lt;% RNA:EACH map/1/users/user %&gt; \n`
 * edge=`&lt;% RNA:EACH map/1/users/user %&gt;`
 *
 * ` // &lt;% RNA:EACH map/1/users/user %&gt; \n`
 * edge=`&lt;% RNA:EACH map/1/users/user %&gt; \n`
 *
 * main=`RNA:EACH map/1/users/user`
 * type=`map`
 * step=`1`
 * expr=`users`
 * expr=`user`
 * </pre>
 *
 * @author trydofor
 * @since 2020-11-01
 */
public class RnaEach extends Tock implements Rng {

    @NotNull
    public final String type;
    public final int step;
    @NotNull
    public final String expr;
    public final boolean mute;

    private RnaWarmed warmed;
    private final String keyTotal;
    private final String keyCount;
    private final String keyFirst;
    private final String keyLast;

    public RnaEach(String text, Clop edge, String tock, @NotNull String type, int step, @NotNull String expr, boolean mute) {
        super(text, edge, tock);
        this.type = type;
        this.step = step;
        this.expr = expr;
        this.mute = mute;
        keyTotal = tock + OBJ$NAVI_DOT + BLT$EACH_TOTAL;
        keyCount = tock + OBJ$NAVI_DOT + BLT$EACH_COUNT;
        keyFirst = tock + OBJ$NAVI_DOT + BLT$EACH_FIRST;
        keyLast = tock + OBJ$NAVI_DOT + BLT$EACH_LAST;
    }

    @Override
    public void check(StringBuilder err, RngChecker rng) {
        warmed = rng.check(err, type, expr);
    }

    @Override
    public void merge(Acid acid, Writer buff) {
        RnaEngine eng = acid.getEngine(type);

        Map<String, Object> ctx = acid.context;
        Object obj = eng.eval(ctx, warmed, mute);

        final int size;
        if (obj instanceof Collection) {
            size = ((Collection<?>) obj).size();
        }
        else if (obj != null && obj.getClass().isArray()) {
            size = Array.getLength(obj);
        }
        else if (obj instanceof Iterator || obj instanceof BaseStream || obj instanceof Iterable) {
            if (obj instanceof BaseStream) {
                obj = ((BaseStream<?, ?>) obj).iterator();
            }
            else if (obj instanceof Iterable) {
                obj = ((Iterable<?>) obj).iterator();
            }
            Iterator<?> items = (Iterator<?>) obj;
            ArrayList<Object> values = new ArrayList<>();
            while (items.hasNext()) {
                Object value = items.next();
                values.add(step < 0 || values.size() % step == 0 ? value : null);
            }
            size = values.size();
            if (step < -1) {
                long stride = -(long) step;
                // Reverse indices are anchored at size - 1, which is unknown until exhaustion.
                for (int index = 0; index < size; index++) {
                    if ((size - 1L - index) % stride != 0) {
                        values.set(index, null);
                    }
                }
            }
            obj = values;
        }
        else {
            size = 0;
        }

        ctx.put(keyTotal, size);
        ctx.put(keyCount, 0);
        ctx.put(keyFirst, false);
        ctx.put(keyLast, false);

        if (size == 0) {
            logger.trace("[👹Merge:tock] skip RNA:EACH tock={}, size={}, step={}, type={}, expr={}", tock, size, step, type, expr);
        }
        else {
            logger.trace("[👹Merge:tock] deal RNA:EACH tock={}, size={}, step={}, type={}, expr={}", tock, size, step, type, expr);
            acid.execute.put(tock, this);
            loop(acid, buff, obj, size, tock, keyCount, keyFirst, keyLast);
        }
    }

    private void loop(Acid acid, Writer buf, Object obj, int size,
                      String keyRefer, String keyCount, String keyFirst, String keyLast) {
        final Map<String, Object> ctx = acid.context;
        final long stride = Math.abs((long) step);
        final int lastc = (int) ((size - 1) / stride + 1);

        if (obj instanceof List && obj instanceof RandomAccess) {
            int count = 1;
            List<?> list = (List<?>) obj;
            if (step > 0) {
                for (long i = 0; i < size; i += step) {
                    ctx.put(keyFirst, count == 1);
                    ctx.put(keyLast, count == lastc);
                    ctx.put(keyRefer, list.get((int) i));
                    ctx.put(keyCount, count++);
                    for (Exon exon : gene) {
                        exon.merge(acid, buf);
                    }
                }
            }
            else {
                for (long i = size - 1; i >= 0; i += step) {
                    ctx.put(keyFirst, count == 1);
                    ctx.put(keyLast, count == lastc);
                    ctx.put(keyRefer, list.get((int) i));
                    ctx.put(keyCount, count++);
                    for (Exon exon : gene) {
                        exon.merge(acid, buf);
                    }
                }
            }
            return;
        }

        if (obj instanceof Collection) {
            int count = 1;
            int index = 0;
            Collection<?> col = (Collection<?>) obj;
            if (step > 0) {
                for (Object it : col) {
                    if (index++ % stride != 0) continue;
                    ctx.put(keyFirst, count == 1);
                    ctx.put(keyLast, count == lastc);
                    ctx.put(keyRefer, it);
                    ctx.put(keyCount, count++);
                    for (Exon exon : gene) {
                        exon.merge(acid, buf);
                    }
                }
                return;
            }
            else {
                if (obj instanceof Deque) {
                    for (Iterator<?> rit = ((Deque<?>) obj).descendingIterator(); rit.hasNext(); ) {
                        Object it = rit.next();
                        if (index++ % stride != 0) continue;
                        ctx.put(keyFirst, count == 1);
                        ctx.put(keyLast, count == lastc);
                        ctx.put(keyRefer, it);
                        ctx.put(keyCount, count++);
                        for (Exon exon : gene) {
                            exon.merge(acid, buf);
                        }
                    }
                    return;
                }
                else {
                    obj = col.toArray();
                }
            }
        }

        // All remaining inputs are arrays; collections either returned or became toArray().
        int count = 1;
        if (step > 0) {
            for (long i = 0; i < size; i += step) {
                ctx.put(keyFirst, count == 1);
                ctx.put(keyLast, count == lastc);
                ctx.put(keyRefer, Array.get(obj, (int) i));
                ctx.put(keyCount, count++);
                for (Exon exon : gene) {
                    exon.merge(acid, buf);
                }
            }
        }
        else {
            for (long i = size - 1; i >= 0; i += step) {
                ctx.put(keyFirst, count == 1);
                ctx.put(keyLast, count == lastc);
                ctx.put(keyRefer, Array.get(obj, (int) i));
                ctx.put(keyCount, count++);
                for (Exon exon : gene) {
                    exon.merge(acid, buf);
                }
            }
        }
    }

    @Override
    public String toString() {
        StringWriter buff = new StringWriter();
        toString(buff);
        return buff.toString();
    }

    public void toString(Writer buff) {
        try {
            buff.append("RnaEach{");
            buff.append("tock='");
            Dent.lineIt(buff, tock);
            buff.append("', type='");
            Dent.lineIt(buff, type);
            buff.append("', step=");
            buff.write(String.valueOf(step));
            buff.append(", expr='");
            Dent.lineIt(buff, expr);
            buff.append("', mute=");
            buff.write(String.valueOf(mute));
            buff.append("}");
            buff.append("; ");
            edge.toString(buff);
        }
        catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
