package pro.fessional.meepo.bind.wow;

import org.junit.jupiter.api.Test;

import pro.fessional.meepo.TraceTest;
import pro.fessional.meepo.bind.Const;

import java.util.Random;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author trydofor
 * @since 2020-10-16
 */
public class LifeTest extends TraceTest {

    @Test
    public void testPoint() {
        // 1,3,5-9
        Life l1 = Life.parse("1,5-7,6-8,5-8,7-9,6-9,5-9,7,7,8,3");
        for (int i = 0; i < 10; i++) {
            assertEquals(Const.TXT$EMPTY, l1.name);
            assertEquals(Life.State.Live, l1.enjoy());
            assertEquals(1, l1.count());
            assertEquals(Life.State.Skip, l1.enjoy());
            assertEquals(2, l1.count());
            assertEquals(Life.State.Live, l1.enjoy());
            assertEquals(3, l1.count());
            assertEquals(Life.State.Skip, l1.enjoy());
            assertEquals(4, l1.count());
            assertEquals(Life.State.Live, l1.enjoy());
            assertEquals(5, l1.count());
            assertEquals(Life.State.Live, l1.enjoy());
            assertEquals(6, l1.count());
            assertEquals(Life.State.Live, l1.enjoy());
            assertEquals(7, l1.count());
            assertEquals(Life.State.Live, l1.enjoy());
            assertEquals(8, l1.count());
            assertEquals(Life.State.Live, l1.enjoy());
            assertEquals(9, l1.count());
            assertEquals(Life.State.Dead, l1.enjoy());
            assertEquals(10, l1.count());
            l1.reset();
        }
    }

    @Test
    public void testOne() {
        Life l1 = Life.parse("");
        for (int i = 0; i < 10; i++) {
            assertEquals(Const.TXT$EMPTY, l1.name);
            assertEquals(Life.State.Live, l1.enjoy());
            assertEquals(1, l1.count());
            assertEquals(Life.State.Dead, l1.enjoy());
            assertEquals(2, l1.count());
            l1.reset();
        }

        Life l2 = Life.parse("1");
        assertEquals(Const.TXT$EMPTY, l2.name);
        assertEquals(Life.State.Live, l2.enjoy());
        assertEquals(1, l2.count());
        assertEquals(Life.State.Dead, l2.enjoy());
        assertEquals(2, l2.count());
    }

    @Test
    public void testAny() {
        Life l1 = Life.parse("*");
        assertEquals(Const.TXT$EMPTY, l1.name);
        for (int i = 1; i <= 256; i++) {
            assertEquals(Life.State.Live, l1.enjoy());
            assertEquals(i, l1.count());
        }
        l1.reset();
        for (int i = 1; i <= 256; i++) {
            assertEquals(Life.State.Live, l1.enjoy());
            assertEquals(i, l1.count());
        }

        Life l2 = Life.parse("id");
        assertEquals("id", l2.name);
        for (int i = 1; i <= 256; i++) {
            assertEquals(Life.State.Live, l2.enjoy());
            assertEquals(i, l2.count());
        }
    }

    @Test
    public void testOther() {
        equalsLive(Life.parse("1"), Life.parse("   1   "));
        equalsLive(Life.parse("1"), Life.parse("   1,   "));
        equalsLive(Life.parse("1,3"), Life.parse("   1,3   "));
        equalsLive(Life.parse("1,3"), Life.parse("   1,3,   "));
        equalsLive(Life.parse("1-3"), Life.parse("   1-3,   "));
    }

    void equalsLive(Life l1, Life l2) {
        assertEquals(l1.toString(), l2.toString());
    }

    @Test
    void aWhitespaceOnlyScopeUsesTheDefaultSingleOccurrence() {
        Life life = Life.parse(" \t\r\n");
        assertEquals(Life.State.Live, life.enjoy());
        assertEquals(Life.State.Dead, life.enjoy());
    }

    @Test
    void extremeEndpointsCannotOverflowTheIntervalOrdering() {
        Life life = Life.parse("0--2147483648,2");
        assertEquals(Life.State.Skip, life.enjoy());
        assertEquals(Life.State.Live, life.enjoy());
        assertEquals(Life.State.Dead, life.enjoy());
    }

    @Test
    void disjointRangesAdvanceTheMergeCursorBeforeLaterOverlaps() {
        check("1,3,3");
        check("1-2,5-7,6-9");
        check("1-2,4,4");
        check("1-2,4-9,4-5,12,12");
        check("1,3-4,3-8,7-10");
    }

    @Test
    void scopesMatchAReferenceSetIncludingDuplicatesAndReversedRanges() {
        Random random = new Random(20261105L);
        for (int trial = 0; trial < 1000; trial++) {
            StringBuilder source = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                if (i != 0) source.append(',');
                source.append(1 + random.nextInt(20));
                if (random.nextBoolean()) source.append('-').append(1 + random.nextInt(20));
            }
            check(source.toString());
        }
    }

    @Test
    void namedScopesMayStartWithPunctuation() {
        Life life = Life.parse("!section");
        assertEquals("!section", life.name);
        assertEquals(Life.State.Live, life.enjoy());
    }

    @Test
    void rangesWithNoPositiveOccurrencesExpireWithoutGoingLive() {
        Life life = Life.parse("0--2");
        assertEquals(Life.State.Skip, life.enjoy());
        assertEquals(Life.State.Dead, life.enjoy());
    }

    private static void check(String source) {
        TreeSet<Integer> expected = new TreeSet<>();
        for (String part : source.split(",")) {
            String[] ends = part.split("-");
            int a = Integer.parseInt(ends[0]);
            int b = ends.length == 1 ? a : Integer.parseInt(ends[1]);
            for (int value = Math.min(a, b); value <= Math.max(a, b); value++) expected.add(value);
        }
        Life life = Life.parse(source);
        int last = expected.last();
        for (int i = 1; i <= last; i++) {
            assertEquals(expected.contains(i) ? Life.State.Live : Life.State.Skip, life.enjoy(), source + " at " + i);
        }
        assertEquals(Life.State.Dead, life.enjoy(), source + " after last occurrence");
        life.reset();
        assertEquals(expected.contains(1) ? Life.State.Live : Life.State.Skip, life.enjoy(), source + " after reset");
    }
}
