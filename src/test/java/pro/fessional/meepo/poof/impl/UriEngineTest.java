package pro.fessional.meepo.poof.impl;

import org.junit.jupiter.api.Test;
import pro.fessional.meepo.TraceTest;
import pro.fessional.meepo.poof.RnaWarmed;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author trydofor
 * @since 2020-10-31
 */
public class UriEngineTest extends TraceTest {

    final UriEngine engine = new UriEngine();
    final Map<String, Object> context = new HashMap<>();

    @Test
    public void evalFile() {
        Object t1 = engine.eval(context, new RnaWarmed("uri", "file://./pom.xml"), false);
        Object t2 = engine.eval(context, new RnaWarmed("uri", "file://./pom.xml"), false);
        Object t3 = engine.eval(context, new RnaWarmed("uri", "./pom.xml"), false);
        assertTrue(((String) t1).length() > 100);
        assertSame(t1, t2);
        assertEquals(t1, t3);
    }

    @Test
    public void evalClass() {
        Object t1 = engine.eval(context, new RnaWarmed("uri", "classpath:/template/blog/blog-pure.htm"), false);
        Object t2 = engine.eval(context, new RnaWarmed("uri", "classpath:/template/blog/blog-pure.htm"), false);
        assertTrue(((String) t1).length() > 100);
        assertSame(t1, t2);
    }

    @Test
    public void evalUrl(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        java.nio.file.Path file = dir.resolve("templates.jar");
        try (java.util.jar.JarOutputStream jar = new java.util.jar.JarOutputStream(java.nio.file.Files.newOutputStream(file))) {
            jar.putNextEntry(new java.util.jar.JarEntry("template.txt"));
            jar.write("本地模板".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            jar.closeEntry();
        }
        String uri = "jar:" + file.toUri() + "!/template.txt";
        Object t1 = engine.eval(context, new RnaWarmed("uri", uri), false);
        Object t2 = engine.eval(context, new RnaWarmed("uri", uri), false);
        assertEquals("本地模板", t1);
        assertSame(t1, t2);
    }
}
