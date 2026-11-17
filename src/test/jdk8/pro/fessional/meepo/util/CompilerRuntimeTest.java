package pro.fessional.meepo.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A real Java 8 JRE image, without its parent JDK's lib/tools.jar.
 */
class CompilerRuntimeTest {
    @Test
    void standaloneJava8RuntimeReportsTheMissingCompiler(@TempDir Path directory) throws Exception {
        Path source = Paths.get(System.getProperty("java.home"));
        Path image = directory.resolve("jre");
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws java.io.IOException {
                Files.createDirectories(image.resolve(source.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws java.io.IOException {
                Files.copy(file, image.resolve(source.relativize(file)));
                return FileVisitResult.CONTINUE;
            }
        });
        Path log = directory.resolve("runtime.log");
        List<String> command = new ArrayList<>();
        command.add(image.resolve("bin/java").toString());
        for (String argument : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (argument.startsWith("-javaagent:") && argument.contains("jacoco")) command.add(argument);
        }
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(CompilerAbsentProbe.class.getName());
        Process child = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(child.waitFor(30, TimeUnit.SECONDS), "Standalone JRE probe timed out");
            assertEquals(0, child.exitValue(), () -> read(log));
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
