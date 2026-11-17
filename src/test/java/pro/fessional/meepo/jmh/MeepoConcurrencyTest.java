package pro.fessional.meepo.jmh;

import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class MeepoConcurrencyTest {
    @Test
    void everyWorkloadAndOutputMatchesItsIndependentRequest() {
        for (String workload : new String[]{ "placeholder", "rows20", "rows200" }) {
            for (String api : new String[]{ "string", "writer", "utf8", "mixed" }) {
                MeepoConcurrency comparison = new MeepoConcurrency();
                comparison.workload = workload;
                comparison.api = api;
                comparison.setup();
                for (int identity = 0; identity < 2; identity++) {
                    MeepoConcurrency.Request request = new MeepoConcurrency.Request();
                    request.initialize(comparison, identity);
                    for (int iteration = 0; iteration < 4; iteration++) {
                        Object actual = comparison.render(request);
                        if (actual instanceof byte[]) {
                            Charset charset = api.equals("mixed") && (iteration & 1) != 0
                                ? Charset.forName("GBK") : StandardCharsets.UTF_8;
                            assertArrayEquals(request.expected.getBytes(charset), (byte[]) actual);
                        }
                        else {
                            assertEquals(request.expected, actual.toString());
                        }
                        assertEquals(3, request.context.size());
                    }
                }
            }
        }
    }
}
