package pro.fessional.meepo.jmh;

import com.samskivert.mustache.Mustache;
import freemarker.template.Configuration;
import gg.jte.CodeResolver;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import gg.jte.output.Utf8ByteOutput;
import gg.jte.output.WriterOutput;
import io.pebbletemplates.pebble.PebbleEngine;
import io.pebbletemplates.pebble.loader.StringLoader;
import org.openjdk.jmh.annotations.*;
import pro.fessional.meepo.sack.Gene;
import pro.fessional.meepo.sack.Holder;
import pro.fessional.meepo.sack.Parser;

import java.io.StringReader;
import java.io.StringWriter;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * All transformations (uppercase, predicates) happen inside rendering, including Mustache getters.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class TemplateComparison {
    @Param({ "meepo", "jte", "jmustache", "mustache", "freemarker", "pebble" })
    public String engine;
    @Param({ "static", "placeholder", "rows20", "rows200" })
    public String workload;
    @Param({ "writer", "string", "native", "utf8" })
    public String api;
    private Renderer renderer;
    String expected;
    private String source;
    private final List<Path> compilationDirectories = new ArrayList<>();
    private final Map<String, Object> context = new HashMap<>();
    private final Model model = new Model();

    public static final class Model {
        public String getName() {
            return "Meepo<&>中文😀";
        }

        public String getUpperName() {
            return getName().toUpperCase(Locale.ROOT);
        }

        public int getId() {
            return 42;
        }

        private final List<Row> rows = new ArrayList<>();

        public List<Row> getRows() {
            return rows;
        }
    }

    public static final class Row {
        private final int id;

        Row(int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }

        public String getName() {
            return "Stock" + id + "<&>中文😀";
        }

        public String getUpperName() {
            return getName().toUpperCase(Locale.ROOT);
        }

        public boolean isNegative() {
            return id % 3 == 0;
        }
    }

    private interface Renderer {
        String string() throws Exception;

        void writer(StringWriter out) throws Exception;

        default String nativeString() throws Exception {
            StringWriter out = new StringWriter();
            writer(out);
            return out.toString();
        }

        default byte[] utf8() throws Exception {
            return string().getBytes(StandardCharsets.UTF_8);
        }
    }

    @Setup
    public void setup() throws Exception {
        int count = workload.equals("rows200") ? 200 : 20;
        for (int i = 0; i < count; i++) model.rows.add(new Row(i + 1));
        context.put("model", model);
        if (workload.equals("static")) {
            source = "<p>Readable template 静态文本😀</p>\n".repeat(64);
            expected = source;
        }
        else if (workload.equals("placeholder")) {
            expected = "Hello " + model.getUpperName() + " (#42)\n";
            source = switch (engine) {
                case "meepo" -> "Hello {{model.upperName}} (#{{model.id}})\n";
                case "jte" -> "@param " + Model.class.getCanonicalName() + " model\nHello ${model.getUpperName()} (#${model.getId()})\n";
                case "jmustache", "mustache" -> "Hello {{model.upperName}} (#{{model.id}})\n";
                case "freemarker" -> "Hello ${model.upperName} (#${model.id})\n";
                case "pebble" -> "Hello {{model.upperName}} (#{{model.id}})\n";
                default -> throw new IllegalArgumentException(engine);
            };
        }
        else {
            StringBuilder out = new StringBuilder();
            for (Row row : model.rows)
                out.append("<row class=\"").append(row.isNegative() ? "minus" : "plus")
                   .append("\">").append(row.getId()).append(':').append(row.getUpperName()).append("</row>\n");
            expected = out.toString();
            source = switch (engine) {
                case "meepo" -> "# HI-MEEPO\n# RNA:EACH /1/model.rows/row\n# RNA:USE /IDX/row.id/*\n"
                                + "# RNA:USE /NAME/row.upperName/*\n# RNA:WHEN /yes/row.negative/sign\n<row class=\"minus\">IDX:NAME</row>\n"
                                + "# RNA:ELSE sign\n<row class=\"plus\">IDX:NAME</row>\n# RNA:DONE sign\n# RNA:DONE row\n";
                case "jte" -> "@param " + Model.class.getCanonicalName() + " model\n@for(var row : model.getRows())\n"
                              + "<row class=\"${row.isNegative() ? \"minus\" : \"plus\"}\">${row.getId()}:${row.getUpperName()}</row>\n@endfor";
                case "jmustache", "mustache" -> "{{#model.rows}}<row class=\"{{#negative}}minus{{/negative}}{{^negative}}plus{{/negative}}\">{{id}}:{{upperName}}</row>\n{{/model.rows}}";
                case "freemarker" -> "<#list model.rows as row><row class=\"<#if row.negative>minus<#else>plus</#if>\">${row.id}:${row.upperName}</row>\n</#list>";
                case "pebble" -> "{% for row in model.rows %}<row class=\"{% if row.negative %}minus{% else %}plus{% endif %}\">{{row.id}}:{{row.upperName}}</row>\n{% endfor %}";
                default -> throw new IllegalArgumentException(engine);
            };
        }
        renderer = create(source);
        verify();
    }

    private Renderer create(String source) throws Exception {
        switch (engine) {
            case "meepo": {
                Gene gene = workload.equals("placeholder") ? Holder.parse(source) : Parser.parse(source);
                return new Renderer() {
                    public String string() {
                        return gene.merge(context, 4096);
                    }

                    public String nativeString() {
                        return gene.merge(context);
                    }

                    public void writer(StringWriter out) {
                        gene.merge(context, out);
                    }

                    public byte[] utf8() throws Exception {
                        ByteArrayOutputStream out = new ByteArrayOutputStream(4096);
                        gene.merge(context, out, StandardCharsets.UTF_8);
                        return out.toByteArray();
                    }
                };
            }
            case "jte": {
                CodeResolver resolver = new CodeResolver() {
                    public String resolve(String name) {
                        return source;
                    }

                    public long getLastModified(String name) {
                        return 0;
                    }
                };
                Path directory = Files.createTempDirectory("meepo-jte-");
                compilationDirectories.add(directory);
                TemplateEngine jte = TemplateEngine.create(resolver, directory, ContentType.Plain);
                jte.setTrimControlStructures(true);
                if (api.equals("utf8")) jte.setBinaryStaticContent(true);
                return new Renderer() {
                    public String string() {
                        StringOutput out = new StringOutput(4096);
                        jte.render("main.jte", model, out);
                        return out.toString();
                    }

                    public String nativeString() {
                        StringOutput out = new StringOutput();
                        jte.render("main.jte", model, out);
                        return out.toString();
                    }

                    public void writer(StringWriter out) {
                        jte.render("main.jte", model, new WriterOutput(out));
                    }

                    public byte[] utf8() {
                        Utf8ByteOutput out = new Utf8ByteOutput(512);
                        jte.render("main.jte", model, out);
                        return out.toByteArray();
                    }
                };
            }
            case "jmustache": {
                com.samskivert.mustache.Template template = Mustache.compiler().escapeHTML(false).compile(source);
                return new Renderer() {
                    public String string() {
                        StringWriter out = new StringWriter(4096);
                        template.execute(context, out);
                        return out.toString();
                    }

                    public String nativeString() {
                        return template.execute(context);
                    }

                    public void writer(StringWriter out) {
                        template.execute(context, out);
                    }
                };
            }
            case "mustache": {
                com.github.mustachejava.DefaultMustacheFactory factory = new com.github.mustachejava.DefaultMustacheFactory() {
                    @Override
                    public void encode(String value, java.io.Writer writer) {
                        try {
                            writer.write(value);
                        }
                        catch (java.io.IOException e) {
                            throw new java.io.UncheckedIOException(e);
                        }
                    }
                };
                var template = factory.compile(new StringReader(source), "main");
                return new Renderer() {
                    public String string() throws Exception {
                        StringWriter out = new StringWriter(4096);
                        writer(out);
                        return out.toString();
                    }

                    public void writer(StringWriter out) throws Exception {
                        template.execute(out, context).flush();
                    }
                };
            }
            case "freemarker": {
                Configuration config = new Configuration(Configuration.VERSION_2_3_31);
                config.setLocale(Locale.ROOT);
                config.setNumberFormat("computer");
                config.setLogTemplateExceptions(false);
                freemarker.template.Template template = new freemarker.template.Template("main", new StringReader(source), config);
                return new Renderer() {
                    public String string() throws Exception {
                        StringWriter out = new StringWriter(4096);
                        template.process(context, out);
                        return out.toString();
                    }

                    public void writer(StringWriter out) throws Exception {
                        template.process(context, out);
                    }
                };
            }
            case "pebble": {
                var template = new PebbleEngine.Builder().loader(new StringLoader()).autoEscaping(false).build().getTemplate(source);
                return new Renderer() {
                    public String string() throws Exception {
                        StringWriter out = new StringWriter(4096);
                        template.evaluate(out, context);
                        return out.toString();
                    }

                    public void writer(StringWriter out) throws Exception {
                        template.evaluate(out, context);
                    }
                };
            }
            default:
                throw new IllegalArgumentException(engine);
        }
    }

    private void verify() throws Exception {
        if (!expected.equals(renderer.nativeString()))
            throw new IllegalStateException(engine + "/" + workload + " default String mismatch");
        if (!expected.equals(renderer.string()))
            throw new IllegalStateException(engine + "/" + workload + " String mismatch: " + renderer.string());
        StringWriter writer = new StringWriter(4096);
        renderer.writer(writer);
        if (!expected.equals(writer.toString()))
            throw new IllegalStateException(engine + "/" + workload + " Writer mismatch");
        if (!Arrays.equals(expected.getBytes(StandardCharsets.UTF_8), renderer.utf8()))
            throw new IllegalStateException(engine + "/" + workload + " UTF-8 mismatch");
    }

    @Benchmark
    public Object render() throws Exception {
        if (api.equals("utf8")) return renderer.utf8();
        if (api.equals("native")) return renderer.nativeString();
        if (api.equals("string")) return renderer.string();
        StringWriter writer = new StringWriter(4096);
        renderer.writer(writer);
        return writer;
    }

    /**
     * Fresh engine/template plus first String output; warm JVM, includes javac for jte.
     */
    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public String compileAndFirstRender() throws Exception {
        return create(source).string();
    }

    @TearDown
    public void cleanup() throws Exception {
        for (Path directory : compilationDirectories)
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        compilationDirectories.clear();
    }

    public static void main(String[] args) throws Exception {
        for (String engine : args)
            for (String workload : List.of("static", "placeholder", "rows20", "rows200"))
                for (String api : List.of("writer", "string", "native", "utf8")) {
                    TemplateComparison state = new TemplateComparison();
                    state.engine = engine;
                    state.workload = workload;
                    state.api = api;
                    try {
                        state.setup();
                        System.out.println(engine + "/" + workload + "/" + api + " verified " + state.expected.length());
                    }
                    finally {
                        state.cleanup();
                    }
                }
    }
}
