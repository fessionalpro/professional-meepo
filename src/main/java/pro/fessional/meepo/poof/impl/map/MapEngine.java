package pro.fessional.meepo.poof.impl.map;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pro.fessional.meepo.eval.JavaEval;
import pro.fessional.meepo.poof.RnaEngine;
import pro.fessional.meepo.poof.RnaWarmed;

import java.util.ArrayList;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import static pro.fessional.meepo.bind.Const.ARR$EMPTY_OBJECT;
import static pro.fessional.meepo.bind.Const.ENGINE$MAP;
import static pro.fessional.meepo.poof.impl.map.MapHelper.KIND_FUNC;

/**
 * Get value from `context`, `System.getProperty` and `System.getenv` in order.
 *
 * @author trydofor
 * @since 2020-10-15
 */
public class MapEngine implements RnaEngine {

    private static final Logger logger = LoggerFactory.getLogger(MapEngine.class);

    private static final String[] TYPE = { ENGINE$MAP };

    @Override
    public @NotNull String[] type() {
        return TYPE;
    }

    @Override
    public @NotNull RnaWarmed warm(@NotNull String type, @NotNull String expr) {
        return MapHelper.warm(type, expr);
    }

    @Override
    public Object eval(@NotNull Map<String, Object> ctx, @NotNull RnaWarmed expr, boolean mute) {
        if (expr == RnaWarmed.EMPTY) return null;
        final ArrayList<RnaWarmed> work = expr.getTypedWork();
        RnaWarmed curr = expr;
        RnaWarmed navi = work.get(0);
        Object obj = null;
        boolean ie = false;
        try {
            curr = navi;
            obj = MapHelper.get(ctx, navi.expr, navi.getTypedWork());

            if (navi.kind == KIND_FUNC) {
                if (!(obj instanceof JavaEval || obj instanceof Function)) {
                    String alias = MapHelper.functionKey(navi);
                    // functionKey returns expr itself when the prefix is already present.
                    if (alias != navi.expr) obj = MapHelper.get(ctx, alias, navi.getTypedWork());
                }
            }
            else {
                if (obj == null) obj = System.getProperty(navi.expr);
                if (obj == null) obj = System.getenv(navi.expr);
            }

            if (obj instanceof Supplier) {
                obj = ((Supplier<?>) obj).get();
            }
            else if (obj instanceof JavaEval) {
                Object[] args = navi.kind == KIND_FUNC ? navi.getTypedWork() : ARR$EMPTY_OBJECT;
                obj = ((JavaEval) obj).eval(ctx, null, args);
            }
            else if (obj instanceof Function) {
                @SuppressWarnings("unchecked")
                Function<Object, Object> fun = (Function<Object, Object>) obj;
                Object arg = navi.kind == KIND_FUNC ? navi.getTypedWork() : null;
                obj = fun.apply(arg);
            }

            for (int i = 1, size = work.size(); i < size; i++) {
                RnaWarmed pip = work.get(i);
                curr = pip;
                Object[] arg = pip.getTypedWork();
                // Customized or simplified mode, function can be without prefixed 'fun:'
                Object cmd = ctx.get(pip.expr);
                if (!(cmd instanceof JavaEval || cmd instanceof Function)) {
                    String alias = MapHelper.functionKey(pip);
                    // The same reference means this key has already been looked up.
                    if (alias != pip.expr) cmd = ctx.get(alias);
                }
                if (cmd instanceof JavaEval) {
                    obj = ((JavaEval) cmd).eval(ctx, obj, arg);
                }
                else if (cmd instanceof Function) {
                    @SuppressWarnings("unchecked")
                    Function<Object, Object> fun = (Function<Object, Object>) cmd;
                    obj = fun.apply(obj);
                }
                else {
                    ie = true;
                    throw new IllegalStateException("failed to get cmd, expr=" + pip);
                }
            }
        }
        catch (Throwable t) {
            if (mute) {
                obj = null;
                logger.warn("mute failed-eval " + expr, t);
            }
            else {
                if (ie) {
                    throw t;
                }
                else {
                    throw new IllegalStateException(expr + ", current=" + curr, t);
                }
            }
        }

        return obj;
    }

    @Override
    public @NotNull RnaEngine fork() {
        return this;
    }
}
