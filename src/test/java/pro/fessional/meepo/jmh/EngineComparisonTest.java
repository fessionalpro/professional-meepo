package pro.fessional.meepo.jmh;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EngineComparisonTest {
    @Test
    void allEnginesProduceIdenticalOutput() throws Exception {
        for (String workload : new String[]{ "static", "placeholder", "control" }) {
            String expected = null;
            for (String engine : new String[]{ "meepo", "pebble", "freemarker" }) {
                for (String api : new String[]{ "writer", "string", "native" }) {
                    EngineComparison comparison = new EngineComparison();
                    comparison.workload = workload;
                    comparison.engine = engine;
                    comparison.api = api;
                    comparison.setup();
                    String actual = comparison.render();
                    if (expected == null) expected = actual;
                    assertEquals(expected, actual, engine + "/" + workload);
                }
            }
        }
    }
}
