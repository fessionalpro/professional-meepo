package pro.fessional.meepo.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class CompilerEnvironmentTest {
    @Test
    void javaOnlyRuntimeReportsTheMissingCompiler(@TempDir Path temporary) throws Exception {
        Path image = temporary.resolve("runtime");
        Path log = temporary.resolve("process.log");
        Path jlink = Path.of(System.getProperty("java.home"), "bin", "jlink");
        assertTrue(Files.isExecutable(jlink), "The Java 17+ comparison profile requires a full JDK");
        run(List.of(jlink.toString(), "--add-modules", "java.base,java.compiler,java.instrument,java.management,java.logging",
            "--output", image.toString()), log);
        List<String> command = new ArrayList<>();
        command.add(image.resolve("bin/java").toString());
        // Pass coverage instrumentation to the child so its valid platform-error
        // branch is merged into the same report, without changing ToolProvider.
        for (String argument : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (argument.startsWith("-javaagent:") && argument.contains("jacoco")) command.add(argument);
        }
        command.addAll(List.of("-cp", System.getProperty("java.class.path"), CompilerAbsentProbe.class.getName()));
        run(command, log);
    }

    private static void run(List<String> command, Path log) throws Exception {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            fail("Timed out: " + command);
        }
        assertEquals(0, process.exitValue(), () -> "Command failed: " + command + "\n" + read(log));
    }

    private static String read(Path log) {
        try {
            return Files.readString(log);
        }
        catch (Exception failure) {
            return failure.toString();
        }
    }
}
