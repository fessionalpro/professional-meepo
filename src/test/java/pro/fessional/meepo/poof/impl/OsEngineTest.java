package pro.fessional.meepo.poof.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import pro.fessional.meepo.TraceTest;
import pro.fessional.meepo.poof.RnaWarmed;

import java.io.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author trydofor
 * @since 2020-10-15
 */
public class OsEngineTest extends TraceTest {

    @Test
    public void testSh() {
        OsEngine engine = new OsEngine();
        RnaWarmed warmed = engine.warm("sh", "me=trydofor;echo $me \\$100");
        Object r1 = engine.eval(new HashMap<>(), warmed, false);
        assertEquals("trydofor $100", r1);
    }

    @Test
    public void testExe() {
        OsEngine engine = new OsEngine();
        HashMap<String, Object> ctx = new HashMap<>();
        ctx.put("me", "trydofor");
        // "/bin/bash", "-c", "echo $u"
        // "cmd", "/c", "echo $u"
        RnaWarmed warmed = engine.warm("exe", "/bin/bash -c \"echo $me \\$100\"");
        Object r1 = engine.eval(ctx, warmed, false);
        assertEquals("trydofor $100", r1);
    }

    public static class EchoArgument {
        public static void main(String[] args) throws Exception {
            System.out.write(args[0].getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    @Test
    @Timeout(10)
    void exePassesShellMetacharactersAsOneLiteralArgument(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) {
        OsEngine engine = new OsEngine();
        java.nio.file.Path marker = dir.resolve("must-not-be-created");
        String value = "literal 中文😀 \"quoted\" \\path; touch " + marker + " $(echo unexpected) | cat & > output";
        String executable = new File(System.getProperty("java.home"), "bin/java").toString();
        String command = quote(executable) + " -cp " + quote(System.getProperty("java.class.path"))
                         + " " + EchoArgument.class.getName() + " " + quote(value);
        assertEquals(value, engine.eval(Collections.emptyMap(), engine.warm("exe", command), false));
        assertFalse(java.nio.file.Files.exists(marker));
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    @Test
    @Timeout(5)
    void cancellationRestoresTheInterruptFlag() {
        OsEngine engine = new OsEngine();
        try {
            Thread.currentThread().interrupt();
            assertEquals("", engine.eval(Collections.emptyMap(), engine.warm("sh",
                "exec 1>&- 2>&-; exec sleep 10"), true));
            assertTrue(Thread.currentThread().isInterrupted(), "Cancellation must remain visible to the caller");
        }
        finally {
            Thread.interrupted();
        }
    }

    @Test
    void invalidEnvironmentEntriesObeyMuteAndNeverStartTheCommand() {
        OsEngine engine = new OsEngine();
        Map<String, Object> invalid = Collections.singletonMap("INVALID=NAME", "value");
        assertEquals("", engine.eval(invalid, engine.warm("exe", "not-a-real-command"), true));
        IllegalStateException error = assertThrows(IllegalStateException.class,
            () -> engine.eval(invalid, engine.warm("exe", "not-a-real-command"), false));
        assertInstanceOf(IllegalArgumentException.class, error.getCause());
        Map<String, Object> value = Collections.singletonMap("INVALID_VALUE", "a" + (char) 0 + "b");
        assertEquals("", engine.eval(value, engine.warm("exe", "not-a-real-command"), true));
    }

    @Test
    @Timeout(10)
    void stderrIsDrainedAndStdinReceivesEof() {
        OsEngine engine = new OsEngine();
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("unused", null);
        assertEquals("ok", engine.eval(ctx, engine.warm("sh",
            "for ((i=0;i<20000;i++)); do echo diagnostic >&2; done; printf ok"), false));
        assertEquals("eof", engine.eval(ctx, engine.warm("sh", "read value || printf eof"), false));
        assertEquals("", engine.eval(ctx, engine.warm("sh", "true"), false));
    }

    @Test
    void closingProcessPipesMayInterruptTheErrorDrain() {
        OsEngine.drainErrors(new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("closed pipe");
            }
        });
    }
}
