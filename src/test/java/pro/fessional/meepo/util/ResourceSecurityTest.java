package pro.fessional.meepo.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pro.fessional.meepo.Meepo;
import pro.fessional.meepo.sack.Parser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class ResourceSecurityTest {
    @Test
    void standardFileUrisDecodeSpacesPercentHashAndUnicode(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("目录 with space % #.txt");
        Files.write(file, "exact 😀".getBytes(StandardCharsets.UTF_8));
        assertEquals("exact 😀", Read.read(file.toUri().toString()));
        assertEquals("exact 😀", Read.read(file.toUri().toString().replaceFirst("file:", "FILE:")));
    }

    @Test
    void fileAuthorityIsNotSilentlyTreatedAsARelativeLocalPath() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
            () -> Read.read("file://untrusted.example/private/template.txt"));
        assertInstanceOf(IllegalArgumentException.class, failure.getCause());
    }

    @Test
    void symlinkAliasesCannotEvadeTheActiveIncludeChain(@TempDir Path dir) throws Exception {
        Path main = dir.resolve("main.tpl");
        Files.write(main, "# HI-MEEPO\n# DNA:SON ./again/main.tpl\n".getBytes(StandardCharsets.UTF_8));
        Files.createSymbolicLink(dir.resolve("again"), dir);
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> Meepo.parse(main.toFile(), 0));
        assertTrue(failure.toString().contains("recursive DNA:SON include"), failure.toString());
    }

    @Test
    void anOriginNeedNotExistAndRepeatedNoncyclicIncludesRemainValid(@TempDir Path dir) throws Exception {
        assertEquals("plain", Parser.parse("plain", dir.resolve("not-created.tpl").toString()).merge(Collections.emptyMap()));
        Files.write(dir.resolve("child.tpl"), "child\n".getBytes(StandardCharsets.UTF_8));
        String source = "# HI-MEEPO\n# DNA:SON ./child.tpl\n# DNA:SON ./child.tpl\n";
        assertEquals("child\nchild\n", Parser.parse(source, dir.resolve("parent.tpl").toString()).merge(Collections.emptyMap()));
    }

    @Test
    void explicitLegacyParentPathsAndNonfileIdentitiesAreStable(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("plain.txt");
        Files.write(file, "legacy".getBytes(StandardCharsets.UTF_8));
        Path cwd = java.nio.file.Paths.get("").toAbsolutePath();
        String relative = cwd.relativize(file.toAbsolutePath()).toString();
        assertEquals("legacy", Read.read("file://../" + cwd.getFileName() + "/" + relative));
        assertEquals("https://example.invalid/template", Read.identity("https://example.invalid/part/../template"));
        assertEquals("classpath:/template", Read.identity("classpath:/part/../template"));
    }
}
