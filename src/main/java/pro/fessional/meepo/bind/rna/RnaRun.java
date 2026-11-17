package pro.fessional.meepo.bind.rna;

import org.jetbrains.annotations.NotNull;
import pro.fessional.meepo.bind.Exon;
import pro.fessional.meepo.bind.kin.Bar;
import pro.fessional.meepo.bind.kin.Prc;
import pro.fessional.meepo.bind.txt.TxtRnaRun;
import pro.fessional.meepo.bind.wow.Clop;
import pro.fessional.meepo.bind.wow.Life;
import pro.fessional.meepo.bind.wow.Tick;
import pro.fessional.meepo.util.Dent;
import pro.fessional.meepo.poof.RnaWarmed;
import pro.fessional.meepo.poof.RngChecker;
import pro.fessional.meepo.sack.Acid;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * RNA:RUN finds replacement positions during parsing and creates TxtRnaRun nodes.
 * Each resulting node evaluates its engine expression when merging reaches it.
 *
 * <pre>
 * ` &lt;% RNA:RUN os/who/basename $(pwd)/1 %&gt; \n`
 * edge=`&lt;% RNA:RUN os/who/basename $(pwd)/1 %&gt;`
 *
 * ` // RNA:RUN os/who/basename $(pwd)/1 \n`
 * edge=`// RNA:RUN os/who/basename $(pwd)/1 \n`
 *
 * main=`RNA:RUN os/who/basename $(pwd)/1`
 * type=`os`
 * name=`who`
 * expr=`basename $(pwd)`
 * life=1
 * </pre>
 *
 * @author trydofor
 * @since 2020-10-16
 */
public class RnaRun extends Tick implements Bar, Prc {

    @NotNull
    public final String type;
    @NotNull
    public final Pattern find;
    @NotNull
    public final String expr;
    public final boolean mute;
    private RnaWarmed warmed;

    public RnaRun(String text, Clop edge, @NotNull Life life, @NotNull String type, @NotNull Pattern find, @NotNull String expr, boolean mute) {
        super(text, edge, life);
        this.type = type;
        this.find = find;
        this.expr = expr;
        this.mute = mute;
    }

    @Override
    public Life.State match(List<Exon.N> lst, String txt) {
        return find.pattern().isEmpty() ? Life.State.Skip : match(lst, txt, find);
    }

    @Override
    public void check(StringBuilder err, RngChecker rng) {
        if (find.pattern().isEmpty()) warmed = rng.check(err, type, expr);
    }

    @Override
    public void merge(Acid acid, Writer buff) {
        if (find.pattern().isEmpty()) acid.getEngine(type).eval(acid.context, warmed, mute);
    }

    @Override
    public @NotNull List<Exon> apply(Clop pos, String txt, int bar) {
        return Collections.singletonList(new TxtRnaRun(txt, pos, type, expr, mute, bar));
    }

    @Override
    public String toString() {
        StringWriter buff = new StringWriter();
        toString(buff);
        return buff.toString();
    }

    public void toString(Writer buff) {
        try {
            buff.append("RnaRun{");
            buff.append("type='");
            Dent.lineIt(buff, type);
            buff.append("', find='");
            Dent.lineIt(buff, find.pattern());
            buff.append("', expr='");
            Dent.lineIt(buff, expr);
            buff.append("', mute=");
            buff.append(String.valueOf(mute));
            buff.append("}");
            buff.append("; ");
            edge.toString(buff);
            buff.append("; ");
            life.toString(buff);
        }
        catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
