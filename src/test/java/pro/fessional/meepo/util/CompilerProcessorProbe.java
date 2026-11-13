package pro.fessional.meepo.util;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Set;

/**
 * Isolated classpath fixture: no provider is registered in normal test resources.
 */
public class CompilerProcessorProbe {
    public static class UnexpectedProcessor extends AbstractProcessor {
        @Override
        public synchronized void init(ProcessingEnvironment environment) {
            super.init(environment);
            try {
                Files.write(Paths.get(System.getProperty("meepo.processor.marker")), new byte[]{ 1 });
            }
            catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public Set<String> getSupportedAnnotationTypes() {
            return Collections.singleton("*");
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latestSupported();
        }

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
            return false;
        }
    }

    public static void main(String[] args) {
        Class<?> result = Java.compile("ProcessorCanary", "public class ProcessorCanary {}\n");
        if (!"ProcessorCanary".equals(result.getName())) throw new AssertionError("Compilation failed");
        if (Files.exists(Paths.get(System.getProperty("meepo.processor.marker"))))
            throw new AssertionError("An implicit annotation processor ran during template compilation");
    }
}
