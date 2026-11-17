package pro.fessional.meepo.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class CompilerSecurityTest {
    @Test
    void templateCompilationNeverDiscoversClasspathAnnotationProcessors(@TempDir Path dir) throws Exception {
        Path provider = dir.resolve("processor.jar"), marker = dir.resolve("unexpected-processor"), log = dir.resolve("child.log");
        String type = CompilerProcessorProbe.UnexpectedProcessor.class.getName();
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(provider))) {
            jar.putNextEntry(new JarEntry("META-INF/services/javax.annotation.processing.Processor"));
            jar.write(type.getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
            String name = type.replace('.', '/') + ".class";
            jar.putNextEntry(new JarEntry(name));
            try (InputStream bytes = getClass().getClassLoader().getResourceAsStream(name)) {
                assertNotNull(bytes);
                byte[] buffer = new byte[4096];
                for (int n; (n = bytes.read(buffer)) >= 0; ) jar.write(buffer, 0, n);
            }
            jar.closeEntry();
        }
        String cp = System.getProperty("java.class.path") + File.pathSeparator + provider;
        Process child = new ProcessBuilder(new File(System.getProperty("java.home"), "bin/java").toString(),
            "-Dmeepo.processor.marker=" + marker, "-cp", cp, CompilerProcessorProbe.class.getName())
            .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(child.waitFor(30, TimeUnit.SECONDS), "Compiler probe timed out");
            assertEquals(0, child.exitValue(), () -> read(log));
            assertFalse(Files.exists(marker), "A classpath annotation processor must not execute implicitly");
        }
        finally {
            child.destroyForcibly();
        }
    }

    private static String read(Path path) {
        try {
            return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        }
        catch (Exception e) {
            return e.toString();
        }
    }
}
