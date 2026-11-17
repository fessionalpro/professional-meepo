package pro.fessional.meepo.poof.impl;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pro.fessional.meepo.poof.RnaEngine;
import pro.fessional.meepo.poof.RnaWarmed;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.util.Map;

import static pro.fessional.meepo.bind.Const.ENGINE$JS;

/**
 * JSR-223 JavaScript adapter using
 * {@link ScriptEngineManager#getEngineByName(String) getEngineByName("JavaScript")}.
 *
 * <p>The JDK-bundled Nashorn engine was deprecated for removal in Java 11
 * (<a href="https://openjdk.org/jeps/335">JEP 335</a>) and removed in Java 15
 * (<a href="https://openjdk.org/jeps/372">JEP 372</a>). Java 21 applications can
 * use the {@code javax.script} API with an external JavaScript provider.</p>
 *
 * <ul>
 * <li><a href="https://github.com/openjdk/nashorn">Standalone Nashorn</a>
 * supports Java 11 and later, including Java 21. Add
 * {@code org.openjdk.nashorn:nashorn-core} and its transitive ASM dependencies
 * to the application runtime. It supports the {@code JavaScript} lookup name
 * used by this adapter.</li>
 * <li><a href="https://github.com/oracle/graaljs/blob/master/docs/user/ScriptEngine.md">GraalJS</a>
 * provides a JSR-223 adapter through {@code org.graalvm.js:js-scriptengine}
 * together with the {@code org.graalvm.polyglot:js} runtime POM. Choose matching
 * versions compatible with the application JDK. Java host access for
 * {@code ctx} requires provider-specific configuration before evaluation.
 * A custom {@link RnaEngine} can perform that initialization.</li>
 * </ul>
 *
 * <p>The application supplies the provider dependencies and registers this
 * adapter with {@code RnaManager.register(new JsEngine())} before parsing
 * JavaScript templates.</p>
 *
 * @author trydofor
 * @since 2020-10-15
 * @deprecated The JDK-bundled Nashorn engine was removed in Java 15. Supply an
 * external JSR-223 provider as described above, or register a provider-specific
 * {@link RnaEngine}.
 */
@Deprecated
public class JsEngine implements RnaEngine {

    private static final Logger logger = LoggerFactory.getLogger(JsEngine.class);
    public static final String CTX_NAME = "ctx";
    private static final ScriptEngineManager MANAGER = new ScriptEngineManager();
    private final ScriptEngine engine = MANAGER.getEngineByName("JavaScript");

    private static final String[] TYPE = { ENGINE$JS };

    @Override
    public @NotNull String[] type() {
        return TYPE;
    }

    @Override
    public Object eval(@NotNull Map<String, Object> ctx, @NotNull RnaWarmed expr, boolean mute) {
        Object obj = null;
        try {
            engine.put(CTX_NAME, ctx);
            obj = engine.eval(expr.expr);
        }
        catch (Throwable t) {
            if (mute) {
                logger.warn("mute failed-eval " + expr, t);
            }
            else {
                throw new IllegalStateException(expr.toString(), t);
            }
        }
        return obj;
    }

    @Override
    public @NotNull RnaEngine fork() {
        return new JsEngine();
    }
}
