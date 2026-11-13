package pro.fessional.meepo.jmh;

import freemarker.template.Configuration;
import freemarker.template.Template;
import io.pebbletemplates.pebble.PebbleEngine;
import io.pebbletemplates.pebble.loader.StringLoader;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import org.openjdk.jmh.annotations.*;
import pro.fessional.meepo.TestingHelper;
import pro.fessional.meepo.sack.Gene;
import pro.fessional.meepo.sack.Holder;
import pro.fessional.meepo.sack.Parser;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Same input model, byte-for-byte output, precompiled templates and writer size.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 2, jvmArgsAppend = { "-Xms256m", "-Xmx256m" })
public class EngineComparison {
    @Param({ "meepo", "pebble", "freemarker" })
    public String engine;
    @Param({ "static", "placeholder", "control" })
    public String workload;
    @Param({ "writer", "string", "native" })
    public String api = "writer";

    private final Map<String, Object> context = new HashMap<>();
    private Gene meepo;
    private PebbleTemplate pebble;
    private Template freemarker;
    private String source;
    private String expected;

    @Setup
    public void setup() throws Exception {
        context.put("user", Collections.singletonMap("name", "Meepo"));
        context.put("id", 42);
        List<Stock> stocks = TestingHelper.mockStocks();
        context.put("items", stocks);
        if ("static".equals(workload)) {
            source = String.join("", Collections.nCopies(64, "<p>Readable template 静态文本</p>\n"));
            expected = source;
        }
        else if ("placeholder".equals(workload)) {
            expected = "Hello MEEPO (#42)\n";
            if ("meepo".equals(engine)) source = "Hello {{user.name | upperCase}} (#{{id}})\n";
            else if ("pebble".equals(engine)) source = "Hello {{user.name | upper}} (#{{id}})\n";
            else source = "Hello ${user.name?upper_case} (#${id})\n";
        }
        else {
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < stocks.size(); i++) {
                Stock stock = stocks.get(i);
                out.append("<row class=\"").append(stock.getChange() < 0 ? "minus" : "plus")
                   .append("\">").append(i + 1).append(':').append(stock.getName().toUpperCase(Locale.ROOT)).append("</row>\n");
            }
            expected = out.toString();
            if ("meepo".equals(engine)) {
                source = "# HI-MEEPO\n# RNA:EACH /1/items/item\n"
                         + "# RNA:USE /IDX/item._count/*\n# RNA:USE /NAME/item.name | upperCase/*\n"
                         + "# RNA:WHEN java/yes/return ((pro.fessional.meepo.jmh.Stock)ctx.get(\"item\")).getChange() < 0;/sign\n"
                         + "<row class=\"minus\">IDX:NAME</row>\n# RNA:ELSE sign\n"
                         + "<row class=\"plus\">IDX:NAME</row>\n# RNA:DONE sign\n# RNA:DONE item\n";
            }
            else if ("pebble".equals(engine)) {
                source = "{% for item in items %}<row class=\"{% if item.change < 0 %}minus{% else %}plus{% endif %}\">"
                         + "{{loop.index + 1}}:{{item.name | upper}}</row>\n{% endfor %}";
            }
            else {
                source = "<#list items as item><row class=\"<#if item.change lt 0>minus<#else>plus</#if>\">"
                         + "${item?index + 1}:${item.name?upper_case}</row>\n</#list>";
            }
        }
        if ("meepo".equals(engine)) {
            meepo = "placeholder".equals(workload) ? Holder.parse(source) : Parser.parse(source);
        }
        else if ("pebble".equals(engine)) {
            pebble = new PebbleEngine.Builder().loader(new StringLoader()).autoEscaping(false).build().getTemplate(source);
        }
        else {
            Configuration config = new Configuration(Configuration.VERSION_2_3_31);
            config.setLocale(Locale.ROOT);
            config.setNumberFormat("computer");
            config.setLogTemplateExceptions(false);
            freemarker = new Template("comparison", new StringReader(source), config);
        }
        String actual = render();
        if (!expected.equals(actual))
            throw new IllegalStateException(engine + "/" + workload + ": output mismatch\n" + actual);
    }

    @Benchmark
    public String render() throws Exception {
        if (meepo != null && "native".equals(api)) return meepo.merge(context);
        if (meepo != null && "string".equals(api)) return meepo.merge(context, 4096);
        StringWriter writer = new StringWriter(4096);
        if (meepo != null) meepo.merge(context, writer);
        else if (pebble != null) pebble.evaluate(writer, context);
        else freemarker.process(context, writer);
        return writer.toString();
    }

}
