package pro.fessional.meepo.bind.txt;

import org.jetbrains.annotations.NotNull;
import pro.fessional.meepo.bind.Exon;
import pro.fessional.meepo.bind.wow.Clop;
import pro.fessional.meepo.sack.Acid;
import pro.fessional.meepo.util.Dent;
import pro.fessional.meepo.pack.Utxt;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

/**
 * Fixed replacement text produced by DNA:SET during parsing.
 * Merging writes the chosen literal without evaluating it as an expression.
 *
 * @author trydofor
 * @since 2020-10-16
 */
public class TxtDnaSet extends Exon {


    private final @NotNull Utxt repl9;

    public TxtDnaSet(@NotNull String text, Clop edge, @NotNull String repl) {
        super(text, edge);
        this.repl9 = new Utxt(repl.toCharArray());
    }

    @Override
    public void merge(Acid acid, Writer buff) {
        Dent.write(buff, repl9);
    }

    @Override
    public String toString() {
        StringWriter buff = new StringWriter();
        toString(buff);
        return buff.toString();
    }

    public void toString(Writer buff) {
        try {
            buff.append("TxtDnaSet{");
            buff.append("repl='");
            Dent.lineIt(buff, repl9);
            buff.append("'}");
            buff.append("; ");
            edge.toString(buff);
        }
        catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
