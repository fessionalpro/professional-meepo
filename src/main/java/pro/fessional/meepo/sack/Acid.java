package pro.fessional.meepo.sack;

import org.jetbrains.annotations.NotNull;
import pro.fessional.meepo.bind.wow.Tock;
import pro.fessional.meepo.poof.RnaEngine;
import pro.fessional.meepo.poof.RnaManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Acid, short for nucleic acid, is the reaction context for one execution of a Gene.
 * <p>Owns copied input bindings, a snapshot of registered functions, forked RNA
 * engines and the winning branch for each named Tock. One merge uses one Acid
 * in one thread, then clears it; separate merges never share these mutable maps.
 * Values inside the input map are copied by reference.</p>
 *
 * @author trydofor
 * @since 2020-11-02
 */
public class Acid {

    @NotNull
    public final Map<String, Tock> execute;
    @NotNull
    public final Map<String, Object> context;
    @NotNull
    private final Map<String, RnaEngine> engines;

    /**
     * Copy caller bindings over the current function snapshot and create the
     * engine instances required by the gene. The caller's map is not modified.
     *
     * @param ctx the context copy from
     * @param rng the engine name
     */
    public Acid(Map<String, Object> ctx, Set<String> rng) {
        this.execute = new HashMap<>();
        this.context = new Cmap(RnaManager.getFunctionSnapshot(), ctx);
        this.engines = new HashMap<>();
        for (String type : rng) engines.put(type, RnaManager.newEngine(type));
    }

    public RnaEngine getEngine(String type) {
        return engines.computeIfAbsent(type, RnaManager::newEngine);
    }

    public void clear() {
        execute.clear();
        context.clear();
        engines.clear();
    }
}
