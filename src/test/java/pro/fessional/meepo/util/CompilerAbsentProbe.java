package pro.fessional.meepo.util;

import javax.tools.ToolProvider;

/**
 * Runs in a real image containing the compiler API but no javac implementation.
 */
public class CompilerAbsentProbe {
    public static void main(String[] args) {
        if (ToolProvider.getSystemJavaCompiler() != null) throw new AssertionError("javac must be absent");
        try {
            Java.compile("NoCompiler", "public class NoCompiler {}\n");
            throw new AssertionError("Expected a missing-compiler diagnostic");
        }
        catch (IllegalStateException failure) {
            if (failure.getCause() == null || !"a JDK compiler is required".equals(failure.getCause().getMessage())) {
                throw new AssertionError("Unexpected diagnostic", failure);
            }
        }
    }
}
