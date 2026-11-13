package pro.fessional.meepo.bind.wow;

import org.jetbrains.annotations.NotNull;
import pro.fessional.meepo.bind.Exon;
import pro.fessional.meepo.pack.Utxt;
import pro.fessional.meepo.util.Dent;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static pro.fessional.meepo.bind.wow.Life.State.Dead;
import static pro.fessional.meepo.bind.wow.Life.State.Live;
import static pro.fessional.meepo.bind.wow.Life.State.Skip;

/**
 * Tick applies a parse-time occurrence counter (Life) to pattern matches.
 * Each participating match advances the counter; Live matches become replacement
 * spans, Skip leaves the source untouched, and Dead retires the processor.
 *
 * @author trydofor
 * @since 2020-10-29
 */
public class Tick extends Exon {

    @NotNull
    public final Life life;

    public Tick(@NotNull String text, @NotNull Clop edge, @NotNull Life life) {
        this(new Utxt(Dent.chars(text, edge)), edge, life);
    }

    protected Tick(@NotNull Utxt text9, @NotNull Clop edge, @NotNull Life life) {
        super(text9, edge);
        this.life = life;
    }

    protected Life.State match(List<N> lst, String txt, Pattern find) {
        Matcher m = find.matcher(txt);
        Life.State st = Skip;
        while (m.find()) {
            int p0 = m.groupCount() > 0 ? m.start(1) : m.start();
            int p1 = m.groupCount() > 0 ? m.end(1) : m.end();
            if (p0 < 0) continue; // Optional capture group did not participate.
            st = life.enjoy();
            if (st == Live) {
                lst.add(new N(p0, p1, this));
            }
            else if (st == Dead) {
                break;
            }
        }
        return st;
    }
}
