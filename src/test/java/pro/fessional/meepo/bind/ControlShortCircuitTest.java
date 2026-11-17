package pro.fessional.meepo.bind;

import org.junit.jupiter.api.Test;
import pro.fessional.meepo.bind.rna.RnaElse;
import pro.fessional.meepo.bind.rna.RnaWhen;
import pro.fessional.meepo.bind.txt.TxtSimple;
import pro.fessional.meepo.bind.wow.Clop;
import pro.fessional.meepo.poof.RngChecker;
import pro.fessional.meepo.sack.Acid;

import java.io.StringWriter;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class ControlShortCircuitTest {
    @Test
    void selectedBranchesSkipPredicatesAndNullEntriesKeepMapSemantics() {
        AtomicBoolean flag = new AtomicBoolean();
        AtomicInteger calls = new AtomicInteger();
        Map<String, Object> input = new HashMap<>();
        input.put("condition", (Supplier<Boolean>) () -> {
            calls.incrementAndGet();
            return flag.get();
        });
        input.put("explode", (Supplier<Boolean>) () -> {
            throw new IllegalArgumentException("evaluated");
        });
        Clop edge = new Clop(0, 0, 1, 1);
        RnaWhen when = new RnaWhen("", edge, "choice", "map", false, "condition", false);
        RnaWhen later = new RnaWhen("", edge, "choice", "map", false, "explode", false);
        RnaElse fallback = new RnaElse("", edge, "choice");
        when.gene.add(new TxtSimple("Y", new Clop(0, 1, 1, 1)));
        fallback.gene.add(new TxtSimple("N", new Clop(0, 1, 1, 1)));
        RngChecker checker = new RngChecker();
        StringBuilder errors = new StringBuilder();
        when.check(errors, checker);
        later.check(errors, checker);
        assertEquals(0, errors.length());
        Acid acid = new Acid(input, checker.getCheckedEngine());
        StringWriter out = new StringWriter();
        acid.execute.put("choice", null);
        when.merge(acid, out);
        assertFalse(acid.execute.containsKey("choice"));
        assertEquals(1, calls.get());
        fallback.merge(acid, out);
        when.merge(acid, out);
        later.merge(acid, out);
        fallback.merge(acid, out);
        assertEquals("NN", out.toString());
        assertEquals(1, calls.get());
        assertSame(fallback, acid.execute.get("choice"));
        acid.execute.clear();
        flag.set(true);
        when.merge(acid, out);
        when.merge(acid, out);
        later.merge(acid, out);
        fallback.merge(acid, out);
        assertEquals("NNYY", out.toString());
        assertEquals(2, calls.get());
        assertSame(when, acid.execute.get("choice"));
        acid.execute.put("choice", null);
        assertThrows(IllegalStateException.class, () -> later.merge(acid, out));
        assertTrue(acid.execute.containsKey("choice"));
        assertNull(acid.execute.get("choice"));
        fallback.merge(acid, out);
        assertSame(fallback, acid.execute.get("choice"));
        acid.clear();
    }
}
