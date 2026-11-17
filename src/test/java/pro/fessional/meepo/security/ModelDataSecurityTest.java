package pro.fessional.meepo.security;

import org.junit.jupiter.api.Test;
import pro.fessional.meepo.TestingHelper;
import org.junit.jupiter.api.io.TempDir;
import pro.fessional.meepo.sack.Gene;
import pro.fessional.meepo.sack.Holder;
import pro.fessional.meepo.sack.Parser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class ModelDataSecurityTest {
    @Test
    void modelTextIsNeverReparsedAsExpressionsOrIncludes(@TempDir Path dir) throws Exception {
        Path secret = dir.resolve("private.txt");
        Files.write(secret, "must-stay-private".getBytes(StandardCharsets.UTF_8));
        String payload = "# HI-MEEPO\n# DNA:SON " + secret.toUri() + "\n{{effect}}<&>\"😀";
        AtomicInteger calls = new AtomicInteger();
        Map<String, Object> input = new HashMap<>();
        input.put("value", payload);
        input.put("effect", (Supplier<Integer>) calls::incrementAndGet);
        Gene holder = Holder.parse("[{{value}}]");
        Gene comment = Parser.parse("# HI-MEEPO\n# RNA:USE /VALUE/value/*\n[VALUE]");
        for (Gene gene : new Gene[]{ holder, comment }) {
            assertEquals("[" + payload + "]", gene.merge(input));
            assertArrayEquals(("[" + payload + "]").getBytes(StandardCharsets.UTF_8), TestingHelper.bytes(gene, input));
        }
        assertEquals(0, calls.get());
        assertEquals(payload, input.get("value"));
    }

    public static class Record {
        private final String secret = "private-canary";

        public String getVisible() {
            return "public";
        }
    }

    @Test
    void propertyLookupDoesNotOpenPrivateModelFields() {
        Gene gene = Holder.parse("{{record.secret}}|{{record.visible}}");
        Map<String, Object> context = Collections.singletonMap("record", new Record());
        assertEquals("|public", gene.merge(context));
        assertEquals("|public", gene.merge(context));
    }
}
