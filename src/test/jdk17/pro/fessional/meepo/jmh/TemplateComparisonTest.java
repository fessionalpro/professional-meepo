package pro.fessional.meepo.jmh;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Executes the same output gate used by the JMH benchmarks against the test dependencies.
 */
class TemplateComparisonTest {
    @Test
    void configuredEnginesProduceTheSameStringsAndUtf8() throws Exception {
        for (String engine : new String[]{ "meepo", "jte", "jmustache", "mustache", "freemarker", "pebble" }) {
            for (String workload : new String[]{ "static", "placeholder", "rows20", "rows200" }) {
                for (String api : new String[]{ "writer", "string", "native", "utf8" }) {
                    TemplateComparison comparison = new TemplateComparison();
                    comparison.engine = engine;
                    comparison.workload = workload;
                    comparison.api = api;
                    try {
                        comparison.setup();
                        Object actual = comparison.render();
                        if (api.equals("utf8")) {
                            assertArrayEquals(comparison.expected.getBytes(StandardCharsets.UTF_8), (byte[]) actual,
                                engine + "/" + workload + "/" + api);
                        }
                        else {
                            assertEquals(comparison.expected, actual.toString(), engine + "/" + workload + "/" + api);
                        }
                    }
                    finally {
                        comparison.cleanup();
                    }
                }
            }
        }
    }
}
