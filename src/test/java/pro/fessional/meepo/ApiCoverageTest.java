package pro.fessional.meepo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pro.fessional.meepo.sack.*;
import pro.fessional.meepo.util.Read;

import java.io.*;
import java.nio.file.*;
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

class ApiCoverageTest {
    private static final Map<String, Object> CTX = Collections.emptyMap();

    private static ByteArrayInputStream stream() {
        return new ByteArrayInputStream("你好".getBytes(UTF_8));
    }

    @Test
    void allInputAndOutputOverloads(@TempDir Path dir) throws Exception {
        File file = dir.resolve("template.txt").toFile();
        Files.write(file.toPath(), "你好".getBytes(UTF_8));
        String uri = file.getAbsolutePath();
        assertEquals("你好", Meepo.parse(file, 0).merge(CTX));
        assertEquals("你好", Meepo.parse("stream-uncached", stream(), 0).merge(CTX));
        assertEquals("你好", Meepo.parse(uri, 0).merge(CTX));
        assertEquals("你好", Meepo.merge(CTX, uri));
        assertEquals("你好", Meepo.merge(CTX, file));
        assertEquals("你好", Meepo.merge(CTX, file, 0));
        assertEquals("你好", Meepo.merge(CTX, "api-stream", stream()));
        assertEquals("你好", Meepo.merge(CTX, "api-text", "你好"));
        assertEquals("你好", Meepo.merge(CTX, "api-text", "你好", 0));
        assertEquals("你好", Read.read(Meepo.steam(CTX, uri)));
        assertEquals("你好", Read.read(Meepo.steam(CTX, uri, 0)));
        assertEquals("你好", Read.read(Meepo.steam(CTX, file)));
        assertEquals("你好", Read.read(Meepo.steam(CTX, file, 0)));
        assertEquals("你好", Read.read(Meepo.steam(CTX, "api-stream2", stream())));
        assertEquals("你好", Read.read(Meepo.steam(CTX, "api-stream3", stream(), 0)));
        assertEquals("你好", Read.read(Meepo.steam(CTX, "api-text2", "你好")));
        assertEquals("你好", Read.read(Meepo.steam(CTX, "api-text3", "你好", 0)));
        assertEquals("你好", Meepo.piece(CTX, "你好", 0));
        assertThrows(IllegalStateException.class, () -> Meepo.parse(dir.resolve("missing").toFile(), 0));
    }

    @Test
    void cacheHonorsTtlAndNeverMode() throws Exception {
        String key = UUID.randomUUID().toString();
        Gene first = Meepo.parse(key, "first", 1);
        assertSame(first, Meepo.parse(key, "second", 1));
        assertNotSame(first, Meepo.parse(key, "uncached", 0));
        // Expire deterministically without making the test wait for wall time.
        java.lang.reflect.Field born = Gene.class.getField("born");
        born.setAccessible(true);
        born.setLong(first, System.currentTimeMillis() - 2000);
        assertEquals("second", Meepo.parse(key, "second", 1).merge(CTX));
        assertEquals("second", Meepo.parse(key, "third", -1).merge(CTX));
    }

    @Test
    void placeholderParsingModes() {
        assertNull(Holder.parse(null));
        assertNull(Holder.parse(true, null));
        assertEquals("x", Holder.parse("x", "", "}").merge(CTX));
        assertEquals("x", Holder.parse(true, "x", "{", "").merge(CTX));
        assertEquals("x", Holder.parse(false, "x").merge(CTX));
        assertEquals("", Holder.parse("").merge(CTX));
        assertEquals("plain", Holder.parse("plain", "{", "}").merge(CTX));
        for (String text : Arrays.asList("x}}", "{{a{{b}}", "{{a}}}}")) {
            assertThrows(IllegalStateException.class, () -> Holder.parse(text), text);
            assertNotNull(Holder.parse(true, text));
        }
        assertEquals("", Holder.parse("{{a|missingFunction}}").merge(CTX));
    }
}
