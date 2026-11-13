package pro.fessional.meepo.bind.wow;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pro.fessional.meepo.bind.Exon;
import pro.fessional.meepo.pack.Utxt;
import pro.fessional.meepo.util.Dent;

import java.io.Writer;
import java.util.ArrayList;

/**
 * Tock is a named runtime control group containing a child gene.
 * WHEN, EACH, ELSE and DONE use the name to coordinate control flow through
 * Acid.execute; Tick handles parse-time matches, while Tock handles merge-time groups.
 *
 * @author trydofor
 * @since 2020-11-01
 */
public class Tock extends Exon {

    protected static final Logger logger = LoggerFactory.getLogger(Tock.class);

    public final @NotNull String tock;
    public final ArrayList<Exon> gene;
    protected final @NotNull Utxt tock9;

    public Tock(@NotNull String text, @NotNull Clop edge, @NotNull String tock) {
        this(new Utxt(Dent.chars(text, edge)), edge, tock);
    }

    protected Tock(@NotNull Utxt text9, @NotNull Clop edge, @NotNull String tock) {
        super(text9, edge);
        this.tock = tock;
        this.gene = new ArrayList<>();
        this.tock9 = new Utxt(tock.toCharArray());
    }

    @Override
    public void build(Writer buff) {
        super.build(buff);
        for (Exon exon : gene) {
            exon.build(buff);
        }
    }
}
