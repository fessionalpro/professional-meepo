package pro.fessional.meepo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pro.fessional.meepo.bind.Exon;
import pro.fessional.meepo.bind.wow.Clop;
import pro.fessional.meepo.bind.txt.TxtSimple;
import pro.fessional.meepo.bind.txt.TxtDnaSet;
import pro.fessional.meepo.bind.dna.DnaRaw;
import pro.fessional.meepo.pack.Ubuf;
import pro.fessional.meepo.sack.Acid;
import pro.fessional.meepo.sack.Gene;
import pro.fessional.meepo.sack.Holder;
import pro.fessional.meepo.sack.Parser;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

class CharsetTemplateTest {
    private static final Charset GBK = Charset.forName("GBK");
    private static final Map<String, Object> MODEL = Collections.singletonMap("value", "动态😀");
    private static final String SOURCE = "# HI-MEEPO\n# DNA:RAW 固定文本\n# DNA:SET /OLD/替换/*\n"
                                         + "# RNA:USE /VALUE/value/*\nOLD VALUE\n";

    @Test
    void oneCharacterPlanSupportsIndependentOutputCharsets() throws Exception {
        Gene gene = Parser.parse(SOURCE);
        String text = gene.merge(MODEL);
        assertTrue(text.contains("固定文本"));
        assertTrue(text.contains("替换 动态😀"));
        for (String name : Arrays.asList("UTF-8", "GBK", "UTF-16", "ISO-2022-JP")) {
            Charset charset = Charset.forName(name);
            byte[] expected = text.getBytes(charset);
            assertArrayEquals(expected, TestingHelper.bytes(gene, MODEL, charset));
            assertArrayEquals(expected, readBytes(Meepo.steam(MODEL, gene, charset)));
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            gene.merge(MODEL, bytes, charset);
            assertArrayEquals(expected, bytes.toByteArray());
            StringWriter writer = new StringWriter();
            gene.merge(MODEL, writer);
            assertEquals(text, writer.toString());
        }
        assertArrayEquals(text.getBytes(UTF_8), TestingHelper.bytes(gene, MODEL));
        assertArrayEquals(text.getBytes(UTF_8), TestingHelper.bytes(gene, MODEL, (Charset) null));
        assertArrayEquals(text.getBytes(UTF_8), readBytes(Meepo.steam(MODEL, gene)));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        gene.merge(MODEL, bytes);
        assertArrayEquals(text.getBytes(UTF_8), bytes.toByteArray());
        bytes.reset();
        gene.merge(MODEL, bytes, null);
        assertArrayEquals(text.getBytes(UTF_8), bytes.toByteArray());
        StringWriter writer = new StringWriter();
        gene.merge(MODEL, writer);
        assertEquals(text, writer.toString());
        bytes.reset();
        Ubuf gbk = new Ubuf(bytes, GBK);
        gene.merge(MODEL, gbk);
        gbk.finish();
        assertArrayEquals(text.getBytes(GBK), bytes.toByteArray());
    }

    @Test
    void streamsKeepIndependentCursorsForColdAndWarmStaticResults() throws Exception {
        for (String text : Arrays.asList("", "中文😀\ud800")) {
            for (String name : Arrays.asList("UTF-8", "GBK", "UTF-16", "ISO-2022-JP")) {
                Charset charset = Charset.forName(name);
                Gene gene = Parser.parse(text);
                byte[] expected = text.getBytes(charset);
                InputStream first = Meepo.steam(MODEL, gene, charset);
                InputStream second = Meepo.steam(MODEL, gene, charset);
                assertEquals(expected.length, first.available());
                assertTrue(first.markSupported());
                first.mark(expected.length);
                assertEquals(expected.length == 0 ? -1 : expected[0] & 0xff, first.read());
                assertArrayEquals(expected, readBytes(second));
                first.reset();
                assertArrayEquals(expected, readBytes(first));
                assertEquals(text, gene.merge(MODEL));
                assertArrayEquals(expected, readBytes(Meepo.steam(MODEL, gene, charset)));
            }
        }
        Gene defaults = Parser.parse("中文😀");
        assertArrayEquals("中文😀".getBytes(UTF_8), readBytes(Meepo.steam(MODEL, defaults, null)));
    }

    @Test
    void dynamicStreamsTransferOnlyCompletedBytesAndDoNotShareLaterRenders() throws Exception {
        Gene gene = Holder.parse("{{value}}");
        for (String name : Arrays.asList("UTF-8", "GBK", "UTF-16", "ISO-2022-JP")) {
            Charset charset = Charset.forName(name);
            for (String value : Arrays.asList("", "x", "\ud800", String.join("", Collections.nCopies(5000, "中文😀")))) {
                InputStream first = Meepo.steam(Collections.singletonMap("value", value), gene, charset);
                InputStream next = Meepo.steam(Collections.singletonMap("value", "later"), gene, charset);
                assertArrayEquals("later".getBytes(charset), readBytes(next));
                assertArrayEquals(value.getBytes(charset), readBytes(first));
            }
        }
    }

    @Test
    void byteInputsUseOnlyTheCallerSuppliedCharset(@TempDir Path directory) throws Exception {
        String expected = Parser.parse(SOURCE).merge(MODEL);
        for (String name : Arrays.asList("UTF-8", "GBK", "GB18030", "UTF-16", "UTF-16LE", "UTF-16BE", "UTF-32")) {
            Charset charset = Charset.forName(name);
            byte[] encoded = SOURCE.getBytes(charset);
            Path file = directory.resolve(name + ".txt");
            Files.write(file, encoded);
            String uri = file.toUri().toString();
            for (Gene gene : Arrays.asList(Meepo.parse(file.toFile(), charset), Meepo.parse(file.toFile(), charset, 0),
                Meepo.parse(uri, charset), Meepo.parse(uri, charset, 0),
                Meepo.parse(uri + "-stream", new ByteArrayInputStream(encoded), charset),
                Meepo.parse(uri + "-stream", new ByteArrayInputStream(encoded), charset, 0),
                Parser.parse(new ByteArrayInputStream(encoded), charset))) {
                assertEquals(expected, gene.merge(MODEL), name);
                assertArrayEquals(expected.getBytes(UTF_8), TestingHelper.bytes(gene, MODEL), name);
                assertArrayEquals(expected.getBytes(GBK), TestingHelper.bytes(gene, MODEL, GBK), name);
            }
            assertEquals(expected, Meepo.merge(MODEL, file.toFile(), charset));
            assertEquals(expected, Meepo.merge(MODEL, file.toFile(), charset, 0));
            assertEquals(expected, Meepo.merge(MODEL, uri, charset));
            assertEquals(expected, Meepo.merge(MODEL, uri, charset, 0));
            assertEquals(expected, Meepo.merge(MODEL, uri + "-merge", new ByteArrayInputStream(encoded), charset));
            assertEquals(expected, Meepo.merge(MODEL, uri + "-merge", new ByteArrayInputStream(encoded), charset, 0));
        }
        Path plain = directory.resolve("plain.txt");
        Files.write(plain, "中文".getBytes(UTF_8));
        assertEquals("中文", Meepo.parse(plain.toFile(), null, 0).merge(MODEL));
        assertEquals("中文", Parser.parse(new ByteArrayInputStream("中文".getBytes(UTF_8)), null).merge(MODEL));
        assertNull(Parser.parse((String) null, null, false, GBK));
        String literal = "# H\0I-MEEPO gbk\n中文";
        assertEquals(literal, Parser.parse(new ByteArrayInputStream(literal.getBytes(UTF_8)), UTF_8).merge(MODEL));
    }

    @Test
    void sourceCachesDoNotReuseAPlanDecodedWithAnotherCharset(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("shared.txt");
        byte[] bytes = "中文".getBytes(UTF_8);
        Files.write(file, bytes);
        Gene utf8 = Meepo.parse(file.toFile(), UTF_8);
        Gene gbk = Meepo.parse(file.toFile(), GBK);
        assertNotSame(utf8, gbk);
        assertEquals("中文", utf8.merge(MODEL));
        assertEquals(new String(bytes, GBK), gbk.merge(MODEL));
        assertSame(gbk, Meepo.parse(file.toFile(), Charset.forName("gbk")));
        assertSame(utf8, Meepo.parse(file.toFile(), (Charset) null));
        String key = file + "-stream";
        Gene first = Meepo.parse(key, new ByteArrayInputStream("中文".getBytes(UTF_8)), UTF_8);
        Gene second = Meepo.parse(key, new ByteArrayInputStream("中文".getBytes(GBK)), GBK);
        assertNotSame(first, second);
        assertEquals(first.merge(MODEL), second.merge(MODEL));
    }

    @Test
    void parserCharsetAppliesToIncludedByteResourcesButNotToTheGene(@TempDir Path directory) throws Exception {
        Files.write(directory.resolve("child.txt"), "# HI-MEEPO\n子模板 VALUE\n# DNA:SON ./grandchild.txt\n".getBytes(GBK));
        Files.write(directory.resolve("grandchild.txt"), "# HI-MEEPO\n孙模板\n".getBytes(GBK));
        String parent = "# HI-MEEPO\n# RNA:USE /VALUE/value/*\n父模板 VALUE\n# DNA:SON ./child.txt\n";
        Path root = directory.resolve("root.txt");
        Files.write(root, parent.getBytes(GBK));
        String expected = "父模板 动态😀\n子模板 VALUE\n孙模板\n";
        for (Gene gene : Arrays.asList(Meepo.parse(root.toFile(), GBK, 0), Parser.parse(parent, root.toString(), false, GBK))) {
            assertEquals(expected, gene.merge(MODEL));
            assertArrayEquals(expected.getBytes(GBK), TestingHelper.bytes(gene, MODEL, GBK));
            assertArrayEquals(expected.getBytes(UTF_8), TestingHelper.bytes(gene, MODEL));
        }
    }

    @Test
    void hiMeepoWordsAreCommentDelimitersRatherThanCharsetDeclarations() {
        String text = "BEGIN HI-MEEPO utf8\nBEGIN DNA:SET /X/Y/* utf8\nX";
        Gene gene = Parser.parse(text);
        assertEquals("Y", gene.merge(MODEL));
        assertArrayEquals("Y".getBytes(GBK), TestingHelper.bytes(gene, MODEL, GBK));
        String notADeclaration = "/* HI-MEEPO utf8:gbk */\nplain";
        assertEquals(notADeclaration, Parser.parse(notADeclaration).merge(MODEL));
    }

    @Test
    void customExonsUseTheWriterEntrypointForEveryByteCharset() {
        Exon legacy = new Exon("", new Clop(0, 0, 1, 1)) {
            @Override
            public void merge(Acid acid, Writer out) {
                try {
                    out.write("中文😀");
                }
                catch (IOException failure) {
                    throw new IllegalStateException(failure);
                }
            }
        };
        Gene gene = new Gene(Collections.singletonList(legacy), Collections.singleton("map"));
        assertArrayEquals("中文😀".getBytes(GBK), TestingHelper.bytes(gene, MODEL, GBK));
        assertArrayEquals("中文😀".getBytes(UTF_8), TestingHelper.bytes(gene, MODEL));
    }

    @Test
    void configuredWritersOwnTheirEncodingAcrossCachesAndWrappers() throws Exception {
        for (String source : Arrays.asList("中文😀\ud800", SOURCE)) {
            Gene gene = Parser.parse(source);
            String expected = gene.merge(MODEL);
            for (String name : Arrays.asList("UTF-8", "GBK", "UTF-16", "ISO-2022-JP")) {
                Charset charset = Charset.forName(name);
                assertArrayEquals(expected.getBytes(charset), TestingHelper.bytes(gene, MODEL, charset));
                ByteArrayOutputStream encoded = new ByteArrayOutputStream();
                try (Writer writer = new BufferedWriter(new OutputStreamWriter(encoded, charset), 3)) {
                    gene.merge(MODEL, writer);
                }
                assertArrayEquals(expected.getBytes(charset), encoded.toByteArray());
                encoded.reset();
                try (Writer writer = new BufferedWriter(new Ubuf(encoded, charset), 3)) {
                    gene.merge(MODEL, writer);
                }
                assertArrayEquals(expected.getBytes(charset), encoded.toByteArray());
            }
        }
    }

    @Test
    void byteOutputUsesAnOverriddenWriterEntrypoint() {
        Gene gene = new Gene(Collections.emptyList(), Collections.emptySet()) {
            @Override
            public void merge(Map<String, Object> context, Writer out) {
                try {
                    out.write("中文😀");
                }
                catch (IOException failure) {
                    throw new IllegalStateException(failure);
                }
            }
        };
        assertArrayEquals("中文😀".getBytes(GBK), TestingHelper.bytes(gene, MODEL, GBK));
        assertArrayEquals("中文😀".getBytes(UTF_8), TestingHelper.bytes(gene, MODEL));
    }

    @Test
    void staticExonsHonorTheConfiguredByteWriter() throws Exception {
        Clop span = new Clop(0, 2, 1, 1);
        Acid acid = new Acid(Collections.emptyMap(), Collections.emptySet());
        for (Exon exon : Arrays.asList(new TxtSimple("中文", span), new TxtDnaSet("中文", span, "中文"),
            new DnaRaw("中文", span, 0, 2))) {
            StringWriter characters = new StringWriter();
            exon.merge(acid, characters);
            assertEquals("中文", characters.toString());
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            Ubuf writer = new Ubuf(bytes, GBK);
            exon.merge(acid, writer);
            writer.finish();
            assertArrayEquals("中文".getBytes(GBK), bytes.toByteArray());
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Parser.parse("中文").merge(MODEL, bytes);
        assertArrayEquals("中文".getBytes(UTF_8), bytes.toByteArray());
        acid.clear();
    }

    @Test
    void inputDecodersCloseStreamsOnSuccessAndFailure() {
        class Input extends ByteArrayInputStream {
            boolean closed;

            Input(String text) {
                super(text.getBytes(GBK));
            }

            @Override
            public void close() {
                closed = true;
            }
        }
        Input input = new Input("中文");
        assertEquals("中文", Parser.parse(input, GBK).merge(MODEL));
        assertTrue(input.closed);
        assertThrows(IllegalStateException.class, () -> Parser.parse(new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("fixture");
            }
        }, GBK));
    }

    private static byte[] readBytes(InputStream stream) throws IOException {
        try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] block = new byte[1024];
            for (int n; (n = input.read(block)) != -1; ) output.write(block, 0, n);
            return output.toByteArray();
        }
    }
}
