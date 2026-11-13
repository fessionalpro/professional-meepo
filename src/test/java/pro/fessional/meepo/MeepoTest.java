package pro.fessional.meepo;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import pro.fessional.meepo.bind.Exon;
import pro.fessional.meepo.bind.wow.Tock;
import pro.fessional.meepo.jmh.Stock;
import pro.fessional.meepo.sack.Gene;
import pro.fessional.meepo.sack.Parser;

import java.io.File;
import java.io.InputStream;
import java.lang.ref.Reference;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author trydofor
 * @since 2024-01-29
 */
public class MeepoTest extends TraceTest {

    @Test
    void meepoNormal() {
        final Gene meepo = Meepo.parse(new File("src/test/resources/template/jmh/stocks.meepo.html"));
        final ArrayList<Stock> items = TestingHelper.mockStocks();

        LinkedList<Stock> lnk = new LinkedList<>(items);
        Map<String, Object> ctx1 = TestingHelper.mockContext(lnk);
        String out1 = TestingHelper.trimOutput(meepo.merge(ctx1));
        Assertions.assertEquals(TestingHelper.ExpectOutputContent, out1);

        Stock[] arr = items.toArray(new Stock[0]);
        Map<String, Object> ctx2 = TestingHelper.mockContext(arr);
        String out2 = TestingHelper.trimOutput(meepo.merge(ctx2));
        Assertions.assertEquals(TestingHelper.ExpectOutputContent, out2);
    }

    @Test
    void meepoReverse() throws Exception {
        final Gene meepo = Meepo.parse("meepoReverse", Files.newInputStream(Paths.get("src/test/resources/template/jmh/stocks.meepo-reverse.html")));
        final ArrayList<Stock> items = TestingHelper.mockStocks();
        Collections.reverse(items);

        LinkedList<Stock> lnk = new LinkedList<>(items);
        Map<String, Object> ctx1 = TestingHelper.mockContext(lnk);
        String out1 = TestingHelper.trimOutput(meepo.merge(ctx1));
        Assertions.assertEquals(TestingHelper.ExpectOutputContent, out1);

        Stock[] arr = items.toArray(new Stock[0]);
        Map<String, Object> ctx2 = TestingHelper.mockContext(arr);
        String out2 = TestingHelper.trimOutput(meepo.merge(ctx2));
        Assertions.assertEquals(TestingHelper.ExpectOutputContent, out2);
    }

    @Test
    void testToString() {
        final Gene meepo = Meepo.parse(new File("src/test/resources/template/jmh/stocks.meepo.html"));
        LinkedHashSet<Exon> exons = new LinkedHashSet<>();
        traval(exons, meepo.exon);
        for (Exon ex : exons) {
            System.out.println(ex);
        }
    }

    void traval(LinkedHashSet<Exon> exon, List<Exon> list) {
        for (Exon ex : list) {
            if (ex instanceof Tock) {
                traval(exon, ((Tock) ex).gene);
            }
            else {
                Assertions.assertFalse(exon.contains(ex), ex.toString());
                exon.add(ex);
            }
        }
    }

    @Test
    void streamsKeepTheirBytesAfterCacheEvictionOrCharsetChanges() throws Exception {
        String value = "中文😀\ud800";
        Gene gene = Parser.parse(value);
        InputStream first = Meepo.steam(Collections.emptyMap(), gene);
        InputStream second = Meepo.steam(Collections.emptyMap(), gene);
        assertEquals(value.getBytes(UTF_8)[0] & 0xff, first.read());
        java.lang.reflect.Field slot = Gene.class.getDeclaredField("text9");
        slot.setAccessible(true);
        ((Reference<?>) slot.get(gene)).clear();
        Meepo.steam(Collections.emptyMap(), gene, Charset.forName("GBK"));
        byte[] expected = value.getBytes(UTF_8);
        byte[] actual = new byte[expected.length];
        assertEquals(actual.length, second.read(actual));
        assertArrayEquals(expected, actual);
        Arrays.fill(actual, (byte) 0);
        InputStream fresh = Meepo.steam(Collections.emptyMap(), gene);
        assertEquals(actual.length, fresh.read(actual));
        assertArrayEquals(expected, actual);
        assertEquals(expected.length - 1, first.available());
        first.close();
        assertEquals(expected.length, Meepo.steam(Collections.emptyMap(), gene).available());
    }
}
