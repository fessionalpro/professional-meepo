package pro.fessional.meepo.bind.rna;

import org.jetbrains.annotations.NotNull;
import pro.fessional.meepo.bind.Const;
import pro.fessional.meepo.bind.wow.Clop;
import pro.fessional.meepo.bind.wow.Tock;
import pro.fessional.meepo.pack.Utxt;
import pro.fessional.meepo.sack.Acid;
import pro.fessional.meepo.util.Dent;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * RNA:DONE closes named control groups in the parser and clears their execution
 * markers during merging, allowing an enclosing loop to execute them again.
 *
 * <pre>
 * ` &lt;% RNA:DONE user pwd %&gt; \n`
 * </pre>
 *
 * @author trydofor
 * @since 2020-11-01
 */
public class RnaDone extends Tock {

    @NotNull
    public final Set<String> name;

    public RnaDone(String text, Clop edge, Collection<String> name) {
        this(new Utxt(Dent.chars(text, edge)), edge, Const.TXT$EMPTY, name);
    }

    protected RnaDone(Utxt text9, Clop edge, @NotNull String tock, Collection<String> name) {
        super(text9, edge, tock);
        if (name instanceof Set) {
            this.name = Collections.unmodifiableSet((Set<String>) name);
        }
        else {
            Set<String> set = new HashSet<>(name);
            this.name = Collections.unmodifiableSet(set);
        }
    }

    public RnaDone copy(String tock) {
        return new RnaDone(text9, edge, tock, name);
    }

    @Override
    public void merge(Acid acid, Writer buff) {
        if (tock.isEmpty()) {
            logger.trace("[👹Merge:tock] skip RNA:DONE tock is empty");
        }
        else {
            logger.trace("[👹Merge:tock] deal RNA:DONE tock={}", tock);
            // An enclosing EACH may revisit this group with a different item.
            acid.execute.remove(tock);
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
            buff.append("RnaDone{");
            buff.append("tock='");
            Dent.lineIt(buff, tock9);
            buff.append("', name=[");
            Dent.lineIt(buff, String.join(",", name));
            buff.append("]}");
            buff.append("; ");
            edge.toString(buff);
        }
        catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
