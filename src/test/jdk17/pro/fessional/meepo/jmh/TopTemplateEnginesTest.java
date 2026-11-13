package pro.fessional.meepo.jmh;

import com.github.mustachejava.DefaultMustacheFactory;
import com.samskivert.mustache.Mustache;
import org.junit.jupiter.api.Test;
import pro.fessional.meepo.TestingHelper;
import pro.fessional.meepo.sack.Gene;
import pro.fessional.meepo.sack.Parser;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Exact-output comparison against the published comparator binaries.
 * JMustache 1.16 requires Java 9 bytecode, so this test runs in jmh-jdk17.
 * EngineComparisonTest retains the Java 8 comparison coverage.
 */
public class TopTemplateEnginesTest {
    public static class Row {
        private final int id;

        Row(int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }

        public String getUpperName() {
            return ("stock" + id + "<&>中文😀").toUpperCase(Locale.ROOT);
        }

        public boolean isNegative() {
            return id % 3 == 0;
        }
    }

    @Test
    void comparatorsMatchMeepoForUnicodePredicatesAndLoopValues() throws Exception {
        List<Row> rows = new ArrayList<>();
        StringBuilder expected = new StringBuilder();
        for (int i = 1; i <= 20; i++) {
            Row row = new Row(i);
            rows.add(row);
            expected.append(row.isNegative() ? "minus:" : "plus:").append(i).append(':').append(row.getUpperName()).append('\n');
        }
        Map<String, Object> context = Collections.singletonMap("rows", rows);
        String source = "{{#rows}}{{#negative}}minus:{{/negative}}{{^negative}}plus:{{/negative}}{{id}}:{{upperName}}\n{{/rows}}";
        String jmustache = Mustache.compiler().escapeHTML(false).compile(source).execute(context);
        DefaultMustacheFactory factory = new DefaultMustacheFactory() {
            @Override
            public void encode(String value, Writer out) {
                try {
                    out.write(value);
                }
                catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        };
        StringWriter output = new StringWriter();
        factory.compile(new StringReader(source), "top-engines").execute(output, context).flush();
        Gene meepo = Parser.parse("# HI-MEEPO\n# RNA:EACH /1/rows/row\n"
                                  + "# RNA:USE /ID/row.id/*\n# RNA:USE /NAME/row.upperName/*\n"
                                  + "# RNA:WHEN /yes/row.negative/sign\nminus:ID:NAME\n# RNA:ELSE sign\nplus:ID:NAME\n"
                                  + "# RNA:DONE sign\n# RNA:DONE row\n");
        assertEquals(expected.toString(), jmustache);
        assertEquals(expected.toString(), output.toString());
        assertEquals(expected.toString(), meepo.merge(context));
        assertArrayEquals(expected.toString().getBytes(UTF_8), TestingHelper.bytes(meepo, context));
    }
}
