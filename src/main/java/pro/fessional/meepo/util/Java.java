package pro.fessional.meepo.util;

import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author trydofor
 * @since 2020-11-04
 */
public class Java {

    public static <T> T create(Class<T> clz, Object... arg) {
        try {
            final int len = arg == null ? 0 : arg.length;
            Class<?>[] pc = new Class<?>[len];
            for (int i = 0; i < len; i++) {
                pc[i] = arg[i].getClass();
            }

            Constructor<T> con = clz.getConstructor(pc);
            return con.newInstance(arg);
        }
        catch (Exception e) {
            throw new IllegalStateException("failed to new instance of " + clz, e);
        }
    }

    public static <T> Class<T> compile(String javaFile, String javaCode) {
        try {
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) throw new IllegalStateException("a JDK compiler is required");
            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

            String fullPath = javaFile.replace('.', '/');
            final Map<String, JavaByte> byteObjects = new HashMap<>();

            try (JavaFileManager fileManager = new ForwardingJavaFileManager<StandardJavaFileManager>
                (compiler.getStandardFileManager(null, null, null)) {
                @Override
                public JavaFileObject getJavaFileForOutput(Location location,
                                                           String className, JavaFileObject.Kind kind,
                                                           FileObject sibling) {
                    JavaByte bytes = new JavaByte(className.replace('.', '/'));
                    byteObjects.put(className, bytes);
                    return bytes;
                }
            }) {

                JavaCode stringObject = new JavaCode(fullPath, javaCode);
                JavaCompiler.CompilationTask task = compiler.getTask(
                    // Runtime expressions need no annotation processors from the host classpath.
                    null, fileManager, diagnostics, Collections.singletonList("-proc:none"), null,
                    Collections.singletonList(stringObject));
                if (!Boolean.TRUE.equals(task.call())) {
                    throw new IllegalStateException("compilation failed: " + diagnostics.getDiagnostics());
                }
            }

            //
            final ClassLoader classLoader = new ClassLoader() {
                @Override
                public Class<?> findClass(String name) throws ClassNotFoundException {
                    JavaByte compiled = byteObjects.get(name);
                    if (compiled == null) throw new ClassNotFoundException(name);
                    byte[] bytes = compiled.getBytes();
                    return defineClass(name, bytes, 0, bytes.length);
                }
            };

            @SuppressWarnings("unchecked")
            Class<T> test = (Class<T>) classLoader.loadClass(javaFile);
            return test;
        }
        catch (Throwable e) {
            throw new IllegalStateException("\njava-file=" + javaFile + "\njava-code=\n" + javaCode, e);
        }
    }

    public static class JavaByte extends SimpleJavaFileObject {
        private final ByteArrayOutputStream bos;

        protected JavaByte(String path) {
            super(URI.create("bytes:///" + path), Kind.CLASS);
            bos = new ByteArrayOutputStream();
        }

        @Override
        public OutputStream openOutputStream() {
            return bos;
        }

        public byte[] getBytes() {
            return bos.toByteArray();
        }
    }

    public static class JavaCode extends SimpleJavaFileObject {
        private final String code;

        protected JavaCode(String path, String code) {
            super(URI.create("string:///" + path + Kind.SOURCE.extension), Kind.SOURCE);
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return code;
        }
    }
}
